(ns casselc.playdoh.impl.clay.item
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.string :as string]
   [charred.api :as charred]
   [dev.onionpancakes.chassis.core :as c]
   [dev.onionpancakes.chassis.compiler :as cc]
   [scicloj.kind-portal.v1.api :as kind-portal]
   [casselc.playdoh.impl.darkstar :refer [vega-spec->svg vega-lite-spec->svg]]
   [casselc.playdoh.impl.clay.util.meta :as meta]
   [casselc.playdoh.impl.clay.files :as files]
   [clojure.tools.logging.readable :as logr])
  (:import (java.awt.image BufferedImage)
           (javax.imageio ImageIO)))


(def *id (atom 0))

(defn next-id! []
  (swap! *id inc))

(defn in-vector [v]
  (if (vector? v)
    v
    [v]))

(defn escape [string]
  (-> string
      (string/escape
       {\< "&lt;" \> "&gt;"})))

(defn clojure-code-item [{:keys [tag hiccup-element md-class]}]
  (fn [string-or-strings]
    (let [strings (->> string-or-strings
                       in-vector)]
                       ;; (map escape)
                       
      {tag true
       :hiccup (cc/compile (->> strings
                                (map (fn [s]
                                       [:pre
                                        [hiccup-element
                                         (escape s)]]))
                                (into [:div])))
       :md (->> strings
                (map (fn [s]
                       (format "
::: {.%s}
```clojure
%s
```
:::
" (name md-class) s)))
                (string/join "\n"))})))

(def source-clojure
  (clojure-code-item {:tag :source-clojure
                      :hiccup-element :code.sourceCode.language-clojure.source-clojure.bg-light
                      :md-class :sourceClojure}))

(def printed-clojure
  (clojure-code-item {:tag :printed-clojure
                      :hiccup-element :code.sourceCode.language-clojure.printed-clojure
                      :md-class :printedClojure}))



(defn just-println [value]
  (-> value
      println
      with-out-str
      ;; escape
      printed-clojure))

(defn pprint [value]
  (-> value
      pp/pprint
      with-out-str
      ;; escape
      printed-clojure))

(defn md [text]
  {:md (->> text
            in-vector
            (string/join "\n"))})

(def separator
  {:hiccup [:div {:style
                  {:height "2px"
                   :width "100%"
                   :background-color "grey"}}]})

(def hidden
  {:md ""
   :hiccup ""})

(defn structure-mark [string]
  {:md string
   :hiccup (cc/compile [:p string])})


(def next-id
  (let [*counter (atom 0)]
    #(str "id" (swap! *counter inc))))

(defn reagent [{:as context
                :keys [value]}]
  (let [id (next-id)]
    {:hiccup (cc/compile [:div {:id id}
                          [:script {:type "application/x-scittle"}
                           (pr-str
                            (list 'reagent.dom/render
                                  value
                                  (list 'js/document.getElementById id)))]])
     :deps (concat [:reagent]
                   (-> context
                       :kindly/options
                       :html/deps)
                   ;; depracated:
                   (-> context
                       :kindly/options
                       :reagent/deps))}))

(defn extract-style [context]
  (-> context
      :kindly/options
      :element/style
      (or {:height "400px"
           :width "100%"})))

(defn cytoscape [{:as context
                  :keys [value]}]
  {:hiccup (cc/compile [:div
                        {:style (extract-style context)}
                        [:script
                         (->> value
                              charred/write-json-str
                              (format
                               "
{
  value = %s;
  value['container'] = document.currentScript.parentElement;
  cytoscape(value);
};"))]])
   :deps [:cytoscape]})


(defn echarts [{:as context
                :keys [value]}]
  {:hiccup (cc/compile [:div
                        {:style (extract-style context)}
                        [:script
                         (->> value
                              charred/write-json-str
                              (format
                               "
{
  var myChart = echarts.init(document.currentScript.parentElement);
  myChart.setOption(%s);
};"))]])
   :deps [:echarts]})


(defn plotly [{:as context
               {:keys [data layout config]
                :or {layout {}
                     config {}}} :value}]
  {:hiccup (cc/compile [:div
                        {:style (extract-style context)}
                        [:script
                         (format
                          "Plotly.newPlot(document.currentScript.parentElement,
              %s, %s, %s);"
                          (charred/write-json-str data)
                          (charred/write-json-str layout)
                          (charred/write-json-str config))]])
   :deps [:plotly]})


(defn portal [value]
  {:hiccup [:div
            [:script
             (->> {:value value}
                  kind-portal/prepare
                  meta/pr-str-with-meta
                  pr-str
                  (format "portal_api.embed().renderOutputItem(
                {'mime': 'x-application/edn',
                 'text': (() => %s)}
                , document.currentScript.parentElement);"))]]
   :deps [:portal]})

(def loader
  {:hiccup [:div.loader]})

(defn info-line [{:keys [path url]}]
  {:hiccup
   (cc/compile [:div
                (when path
                  [:pre
                   [:small
                    [:small
                     "source: "
                     (if url
                       [:a {:href url} path]
                       path)]]])])})


(defn html [html]
  {:html (->> html
              in-vector
              (string/join "\n"))})


(defn write! [^BufferedImage image
              format
              target-jpg-path]
  (io/make-parents target-jpg-path)
  (ImageIO/write image
                 format
                 (io/file target-jpg-path)))

(defn image [{:keys [value
                     full-target-path
                     base-target-path]
              :as _context}]
  (let [png-path (files/next-file!
                  full-target-path
                  ""
                  value
                  ".png")]
    (when-not
     (write! value "png" png-path)
     (throw (ex-message "Failed to save image as PNG.")))
    {:hiccup (cc/compile [:img {:src (-> png-path
                                         (string/replace
                                          (re-pattern (str "^"
                                                           base-target-path
                                                           "/"))
                                          ""))}])
     :item-class "clay-image"}))


(defn vega-svg 
  [{:keys [value]}]
  {:hiccup (cc/compile [:div
                        (c/raw
                         (vega-spec->svg (charred/write-json-str value)))])})

(defn vega-lite-svg
  [{:keys [value]}]
  (logr/debugf  "Rendering %s" value)
  {:hiccup [:div
            (c/raw (vega-lite-spec->svg (charred/write-json-str value)))]})

(defn vega-embed [{:keys [value
                          full-target-path
                          base-target-path]
                   :as _context}]
  (let [{:keys [data]} value
        data-to-use (or (when-let [{:keys [values format]} data]
                          (when (some-> format :type name (= "csv"))
                            (let [csv-path (files/next-file!
                                            full-target-path
                                            ""
                                            values
                                            ".csv")]
                              (spit csv-path values)
                              {:url (-> csv-path
                                        (string/replace
                                         (re-pattern (str "^"
                                                          base-target-path
                                                          "/"))
                                         ""))
                               :format format})))
                        data)]
    {:hiccup (cc/compile [:div
                          [:script (-> value
                                       (assoc :data data-to-use)
                                       charred/write-json-str
                                       (->> (format "vegaEmbed(document.currentScript.parentElement, %s);")))]])
     :deps [:vega]}))


(defn video [{:keys [youtube-id
                     iframe-width
                     iframe-height
                     allowfullscreen
                     embed-options]
              :or {allowfullscreen true}}]
  {:hiccup (cc/compile [:iframe
                        (merge
                         (when iframe-height
                           {:height iframe-height})
                         (when iframe-width
                           {:width iframe-width})
                         {:src (str "https://www.youtube.com/embed/"
                                    youtube-id
                                    (some->> embed-options
                                             (map (fn [[k v]]
                                                    (format "%s=%s" (name k) v)))
                                             (string/join "&")
                                             (str "?")))
                          :allowfullscreen allowfullscreen})])})

(defn observable [code]
  {:md (->> code
            in-vector
            (string/join "\n")
            (format "
```{ojs}
//| echo: false
%s
```"))})

(defn ggplotly [spec]
  (let [id (str "htmlwidget-1" (next-id!))]
    {:hiccup (cc/compile [:div {:class "plotly html-widget html-fill-item-overflow-hidden html-fill-item"
                                :id id
                                :style "width:100%;height:400px;"}
                          [:script {:type "application/htmlwidget-sizing"
                                    :data-for id}
                           (charred/write-json-str {:viewer {:width "100%"
                                                             :height 400
                                                             :padding "0"
                                                             :fille true}
                                                    :browser {:width "100%"
                                                              :height 400
                                                              :padding "0"
                                                              :fille true}})]
                          [:script {:type "application/json"
                                    :data-for id}
                           (charred/write-json-str spec)]])
     :deps [:htmlwidgets-ggplotly]}))


(def avoid-favicon-html
  ;; avoid favicon.ico request: https://stackoverflow.com/a/38917888
  (c/html
   [:link {:rel "icon" :href "data:,"}]))

(defn highcharts
  "Prepare Highcharts JSON data for plotting."
  [value]
  {:hiccup (cc/compile [:div
                        [:script
                         (format
                          "Highcharts.chart(document.currentScript.parentElement, %s);"
                          (charred/write-json-str value))]])
   :deps [:highcharts]})