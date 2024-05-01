(ns casselc.playdoh.impl.clay.prepare
  (:require
   [clojure.tools.logging.readable :as logr]
   [clojure.walk]
   [clojure.string :as string]
   [charred.api :as charred]
   [nextjournal.markdown :as md]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [casselc.playdoh.impl.clay.util.claywalk :as claywalk]
   [casselc.playdoh.impl.clay.item :as item]
   [casselc.playdoh.impl.clay.table :as table]
   [dev.onionpancakes.chassis.core :as c]))

(defn deep-merge
  "Recursively merges maps.
  See https://dnaeon.github.io/recursively-merging-maps-in-clojure/. "
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(def *kind->preparer
  (atom {}))

(defn add-preparer!
  [kind preparer]
  (swap! *kind->preparer assoc kind preparer)
  [:ok])


(defn preparer-from-value-fn [f]
  (comp f :value))

(defn add-preparer-from-value-fn!
  [kind f]
  (->> f
       preparer-from-value-fn
       (add-preparer! kind)))

(defn value->kind [v]
  (-> {:value v}
      kindly-advice/advise
      :kind))

(defn add-class-to-class-str [class-str cls]
  (if class-str
    (str class-str " " cls)
    cls))

(defn add-class-to-hiccup [hiccup cls]
  (if cls
    (if (-> hiccup second map?)
      (update-in hiccup [1 :class] #(add-class-to-class-str % cls))
      (vec (concat [(first hiccup)
                    {:class cls}]
                   (rest hiccup))))
    hiccup))

(defn item->hiccup [{:keys [hiccup html md
                            script
                            item-class
                            inside-a-table]}
                    {:as _context
                     :keys [format _kind]}]
  (-> (or hiccup
          (some->> html
                   (vector :div))
          (when md
            (if (and (vector? format)
                     (-> format first (= :quarto))
                     inside-a-table)
              [:span {:data-qmd md}]
              (->> md
                   md/->hiccup
                   (clojure.walk/postwalk-replace {:<> :p})
                   (clojure.walk/postwalk-replace {:table :table.table})))))
      (add-class-to-hiccup item-class)
      (cond-> script
        (conj script))))

(defn limit-hiccup-height [hiccup context]
  (when hiccup
    (if-let [max-element-height (-> context
                                    :kindly/options
                                    :element/max-height)]
      [:div {:style {:max-height max-element-height
                     :overflow-y :auto}}
       hiccup]
      hiccup)))

(defn limit-md-height [md context]
  (when md
    (if-let [max-element-height (-> context
                                    :kindly/options
                                    :element/max-height)]
      (c/html

       [:div {:style {:max-height max-element-height
                      :overflow-y :auto}}
        md])
      md)))

(defn advise-if-needed [context]
  (if (:advise context)
    context
    (kindly-advice/advise context)))

(defn prepare [{:as context
                :keys [value]}
               {:keys [fallback-preparer]}]
  (logr/info "Prepare" value "with" context)
  (let [complete-context (-> context
                             (update :kindly/options
                                     deep-merge
                                     (-> value meta :kindly/options)))
        kind (-> complete-context
                 advise-if-needed
                 :kind)]
    (logr/info "Will prepare a" kind)
    (case kind
      :kind/fragment (->> value
                          ;; splice the fragment
                          (mapcat (fn [subvalue]
                                    (-> context
                                        (assoc :value subvalue)
                                        (prepare {:fallback-preparer fallback-preparer})))))
      :kind/fn (let [[f & args] value
                     ;; apply the fn
                     new-value (apply f args)]
                 (-> context
                     (assoc :value new-value)
                     (dissoc :form)
                     (dissoc :kind)
                     (prepare {:fallback-preparer fallback-preparer})))
      ;; else - a regular kind
      (when-let [preparer (-> kind
                              (@*kind->preparer)
                              (or fallback-preparer))]
        (logr/spyf "Prepare result:\n%s\n" [(-> complete-context
                                                preparer
                                                (update :hiccup limit-hiccup-height complete-context)
                                                (update :md limit-md-height complete-context))])))))

(defn prepare-or-pprint [context]
  (prepare context {:fallback-preparer
                    (preparer-from-value-fn #'item/pprint)}))

(defn has-kind-with-preparer? [value]
  (some-> value
          value->kind
          (@*kind->preparer)))

(add-preparer-from-value-fn!
 :kind/println
 #'item/just-println)

(add-preparer-from-value-fn!
 :kind/pprint
 #'item/pprint)

(add-preparer-from-value-fn!
 :kind/hidden
 (constantly item/hidden))

(add-preparer-from-value-fn!
 :kind/md
 #'item/md)

(add-preparer!
 :kind/table
 (fn [{:as context
       :keys [value]}]
   (let [use-datatables (->> context
                             :kindly/options
                             :use-datatables)
         pre-hiccup (table/->table-hiccup value)
         *deps (atom []) ; TODO: implement without mutable state
         hiccup (->> pre-hiccup
                     (claywalk/postwalk
                      (fn [elem]
                        (if (and (vector? elem)
                                 (-> elem first (#{:th :td})))
                          ;; a table cell - handle it
                          [(first elem) (if-let [items (-> context
                                                           (dissoc :form)
                                                           (update :kindly/options dissoc :element/max-height)
                                                           (assoc :value (second elem))
                                                           (prepare {}))]
                                          (do (swap! *deps concat (mapcat :deps items))
                                              (map (fn [item]
                                                     (-> item
                                                         (assoc :inside-a-table true)
                                                         (item->hiccup
                                                          (-> context
                                                              (cond-> use-datatables
                                                                (assoc :format [:html]))))))
                                                   items))
                                          ;; else - a plain value
                                          (second elem))]
                          ;; else - keep it
                          elem))))]
     (merge
      {:hiccup hiccup
       :item-class "clay-table"}
      (if use-datatables
        {:script [:script
                  (->> context
                       :kindly/options
                       :datatables
                       charred/write-json-str
                       (format "new DataTable(document.currentScript.parentElement, %s);"))]
         :deps (->> @*deps
                    (cons :datatables)
                    distinct)}
        ;; else
        {:deps (distinct @*deps)})))))

(defn structure-mark-hiccup [mark]
  (-> mark
      item/structure-mark
      :hiccup))

(add-preparer-from-value-fn!
 :kind/code
 (fn [codes]
   (->> codes
        item/source-clojure)))

(add-preparer-from-value-fn!
 :kind/dataset
 (fn [v]
   (logr/info "Creating a dataset from:" v)
   (logr/spyf :info "Dataset result:\n%s\n"
              (-> v
                  println
                  with-out-str
                  item/md
                  (merge {:item-class "clay-dataset"})))))

(add-preparer-from-value-fn!
 :kind/smile-model
 (fn [v]
   (-> v
       str
       item/printed-clojure)))

(add-preparer!
 :kind/test
 (fn [{:as context
       :keys [value]}]
   (-> context
       (dissoc :form)
       (update :kindly/options :dissoc :element/max-height)
       (assoc :value (-> value
                         meta
                         :test
                         (#(%))))
       prepare-or-pprint)))

(add-preparer!
 :kind/map
 (fn [{:as context
       :keys [value]}]
   (if (->> value
            (apply concat)
            (some has-kind-with-preparer?))
     (let [*deps (atom []) ; TODO: implement without mutable state
           prepared-kv-pairs (->> value
                                  (map (fn [kv]
                                         {:kv kv
                                          :prepared-kv
                                          (->> kv
                                               (mapcat (fn [v]
                                                         (let [items (-> context
                                                                         (dissoc :form)
                                                                         (update :kindly/options :dissoc :element/max-height)
                                                                         (assoc :value v)
                                                                         prepare-or-pprint)]
                                                           (swap! *deps concat (mapcat :deps items))
                                                           items))))})))]
       (if (->> prepared-kv-pairs
                (mapcat :prepared-kv)
                (some (complement :printed-clojure)))
         ;; some parts are not just printed values - handle recursively
         {:hiccup [:div
                   (structure-mark-hiccup "{")
                   (->> prepared-kv-pairs
                        (map (fn [{:keys [kv prepared-kv]}]
                               (if (->> prepared-kv
                                        (some (complement :printed-clojure)))
                                 (let [[pk pv] prepared-kv]
                                   [:table
                                    [:tr
                                     [:td {:valign :top}
                                      (item->hiccup pk context)]
                                     [:td [:div
                                           {:style {:margin-left "10px"}}
                                           (item->hiccup pv context)]]]])
                                 ;; else
                                 (item->hiccup (->> kv
                                                    (map pr-str)
                                                    (string/join " ")
                                                    item/printed-clojure)
                                               context))))
                        (into [:div
                               {:style {:margin-left "10%"
                                        :width "110%"}}]))
                   (structure-mark-hiccup "}")]
          :deps (distinct @*deps)}
         ;; else -- just print the whole value
         (item/pprint value)))
     ;; else -- just print the whole value
     (item/pprint value))))

(defn view-sequentially [{:as context
                          :keys [value]}
                         open-mark close-mark]
  (if (->> value
           (some has-kind-with-preparer?))
    (let [_*deps (atom []) ; TODO: implement without mutable state
          prepared-parts (->> value
                              (mapcat (fn [subvalue]
                                        (-> context
                                            (dissoc :form)
                                            (update :kindly/options :dissoc :element/max-height)
                                            (assoc :value subvalue)
                                            prepare-or-pprint))))]
      (if (->> prepared-parts
               (some (complement :printed-clojure)))
        ;; some parts are not just printed values - handle recursively
        {:hiccup [:div
                  (structure-mark-hiccup open-mark)
                  (->> prepared-parts
                       (map #(item->hiccup % context))
                       (into [:div {:style {:margin-left "10%"
                                            :width "110%"}}]))
                  (structure-mark-hiccup close-mark)]
         :deps (->> prepared-parts
                    (mapcat :deps)
                    distinct)}
        ;; else -- just print the whole value
        (item/pprint value)))
    ;; else -- just print the whole value
    (item/pprint value)))

(add-preparer!
 :kind/vector
 (fn [context]
   (view-sequentially context "[" "]")))

(add-preparer!
 :kind/seq
 (fn [context]
   (view-sequentially context "(" ")")))

(add-preparer!
 :kind/set
 (fn [context]
   (view-sequentially context "#{" "}")))

(def next-id
  (let [*counter (atom 0)]
    #(str "id" (swap! *counter inc))))

(add-preparer!
 :kind/reagent
 #'item/reagent)

(def non-hiccup-kind?
  (complement #{:kind/vector :kind/map :kind/seq :kind/set
                :kind/hiccup}))

(defn wrap-with-div-if-many [hiccups]
  (if (-> hiccups
          count
          (> 1))
    (into [:div] hiccups)
    (first hiccups)))

(add-preparer!
 :kind/hiccup
 (fn [{:as context
       :keys [value]}]
   (let [*deps (atom
                (-> context
                    :kindly/options
                    :html/deps)) ; TODO: implement without mutable state
         hiccup (->> value
                     (claywalk/prewalk
                      (fn [subform]
                        (let [subcontext (-> context
                                             (dissoc :form)
                                             (update :kindly/options dissoc :element/max-height)
                                             (assoc :value subform))]
                          (if (some-> subcontext
                                      kindly-advice/advise
                                      :kind
                                      non-hiccup-kind?)
                            (let [items (prepare-or-pprint
                                         subcontext)]
                              (swap! *deps concat (mapcat :deps items))
                              (->> items
                                   (map #(item->hiccup % context))
                                   wrap-with-div-if-many))
                            subform)))))]
     {:hiccup hiccup
      :deps (distinct @*deps)})))

(add-preparer-from-value-fn!
 :kind/html
 #'item/html)

(add-preparer-from-value-fn!
 :kind/portal
 (fn [value]
   (-> value
       (vary-meta dissoc :kindly/kind)
       item/portal)))

(add-preparer!
 :kind/cytoscape
 #'item/cytoscape)

(add-preparer!
 :kind/echarts
 #'item/echarts)

(add-preparer!
 :kind/plotly
 #'item/plotly)

(add-preparer!
 :kind/image
 #'item/image)

(add-preparer!
 :kind/vega
 #'item/vega-svg)

(add-preparer!
 :kind/vega-lite
 #'item/vega-lite-svg)

(add-preparer-from-value-fn!
 :kind/video
 #'item/video)

(add-preparer-from-value-fn!
 :kind/observable
 #'item/observable)

(add-preparer-from-value-fn!
 :kind/htmlwidgets-ggplotly
 #'item/ggplotly)

(add-preparer-from-value-fn!
 :kind/highcharts
 #'item/highcharts)