(ns casselc.playdoh.ui
  (:require
   [clojure.tools.logging.readable :as logr]
   [dev.onionpancakes.chassis.core :as c]
   [dev.onionpancakes.chassis.compiler :as cc]
   [casselc.playdoh.impl.clay.prepare :as prepare]))

(set! *warn-on-reflection* true)

(let [colors (atom (cycle (shuffle ["bg-blue-500" "bg-red-500" "bg-orange-500" "bg-lime-500" "bg-green-500" "bg-teal-500" "bg-emerald-500" "bg-cyan-500" "bg-sky-500" "bg-indigo-500" "bg-violet-500" "bg-purple-500" "bg-fuchsia-500" "bg-pink-500" "bg-rose-500"])))]
  (defn bg-color
    []
    (ffirst (swap-vals! colors rest))))

(defn value->hiccup
  [value kind]
  (try
    (-> {:value value
         :kind kind}
        prepare/prepare-or-pprint
        first
        (prepare/item->hiccup {}))
    (catch Exception e
      [:div
       (ex-message e)])))

(def kinds [["Default" nil]
            ["Apache EChart" :kind/echarts]
            ["Code" :kind/code]
            ["Cytoscape" :kind/cytoscape]
            ["Dataset" :kind/dataset]
            ["EDN" :kind/edn]
            ["Fragment" :kind/fragment]
            ["Function" :kind/fn]
            ["HTML" :kind/html]
            ["HTMLWidgets ggplotly" :kind/htmlwidgets-ggplotly]
            ["HTMLWidgets plotly" :kind/htmlwidgets-plotly]
            ["Hiccup" :kind/hiccup]
            ["Hidden" :kind/hidden]
            ["Highcharts" :kind/highcharts]
            ["Image" :kind/image]
            ["Map" :kind/map]
            ["Markdown" :kind/md]
            ["Observable" :kind/observable]
            ["Plotly" :kind/plotly]
            ["Portal viewer" :kind/portal]
            ["Pretty-print" :kind/pprint]
            ["Reagent component" :kind/reagent]
            ["Sequence" :kind/seq]
            ["Set" :kind/set]
            ["Smile Model" :kind/smile-model]
            ["Table" :kind/table]
            ["Test" :kind/test]
            ["Var" :kind/var]
            ["Vector" :kind/vector]
            ["Vega Plot" :kind/vega]
            ["Vega-Lite Plot" :kind/vega-lite]
            ["Video" :kind/video]])

(defn kind-select
  [id]
  (let [select-id (str "kind-select" id)
        options (map (fn [[kind]] [:option {:value kind} kind]) kinds)]
    (cc/compile
     [:label.flex.flex-row.gap-2.items-center
      [:h5.text-lg "Kind"]
      [:select.text-xs {:id select-id
                        :name "kind"
                        :hx-post "/evaluate"}
       options]])))

(defn editor-pane
  [{:keys [id text]}]
  (let [editor-id (str "editor-" id)]
    (cc/compile
     [:textarea.w-full.h-full.text-xs
      {:id editor-id
       :name "text"
       :required true
       :cols 40
       :rows 10
       :autocapitalize "off"
       :autocomplete "off"
       :autocorrect "off"
       :spellcheck false}
      text])))

(defn result-pane
  [{:keys [result kind]}]
  (logr/info "Creating result of kind" kind "for" result)
  (let [result-hiccup (value->hiccup result kind)]
    (cc/compile
     [:div.bg-gray-100.h-full
      result-hiccup])))

(defn notebook-cell
  [{:keys [id] :as cell}]
  (let [cell-id (str "cell-" id)
        result-id (str "result-" id)
        result-target (str "#" result-id)
        select (kind-select id)
        editor (editor-pane cell)
        result (result-pane cell)]
    (cc/compile
     [:form.flex.flex-col.w-full.drop-shadow-md.bg-yellow-300
      {:action "/evaluate"
       :hx-post "/evaluate"
       :hx-target result-target
       :hx-swap "innerHTML"}
      [:input {:name "id"
               :type "hidden"
               :value id}]
      [:fieldset.flex.flex-row.justify-between.p-1.-mx-2
       {:class [(bg-color)]}
       [:button.border.border-1.border-black.bg-white.rounded-md.drop-shadow-2xl.p-1        "Evaluate"]
       select]
      [:div.flex.flex-row
       {:id cell-id}
       [:div.w-full.p-1 editor]
       [:div.w-full.p-1 {:id result-id} result]]])))

(defn add-row-button
  []
  (cc/compile
   [:li#cell-target.flex.flex-row.justify-center
    [:form
     {:action "/cell"
      :hx-post "/cell"
      :hx-target "#cell-target"
      :hx-swap "outerHTML"}
     [:button.p-1.border.border-black.border-1.rounded-md.shadow-md
      {:type "submit"
       :name "type"
       :value "clojure"
       :class [(bg-color)]}
      "Add Row"]]]))

(defn index-page
  [cell-list cells]
  (let [notebook-cells (map (fn [cell] [:li.flex.flex-row.justify-center (notebook-cell (cells cell))]) cell-list)]
    (cc/compile
     [c/doctype-html5
      [:html
       [:head
        [:link {:rel "icon" :href "data:,"}]
        [:meta {:charset "UTF-8"}]
        [:meta {:http-equiv "Content-Type" :content "text/html"}]
        [:title "Playdoh - a Clojure data playground"]
        [:script
         {:src "https://unpkg.com/htmx.org@1.9.12"
          :integrity
          "sha384-ujb1lZYygJmzgSwoxRggbCHcjc0rB2XoQrxeTUQyRjrOnlCoYta87iKBWq3EsdM2"
          :crossorigin "anonymous"}]
        [:link {:rel "stylesheet" :href "https://cdn.datatables.net/1.13.6/css/jquery.dataTables.min.css"}]
        [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css"}]
        [:link {:rel "stylesheet" :href "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"}] 
        [:script {:src "https://cdn.tailwindcss.com?plugins=forms" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/vega@5.25.0" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/vega-lite@5.16.3" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/vega-embed@6.22.2" :async true}]
        [:script {:src "https://cdn.datatables.net/1.13.6/js/jquery.dataTables.min.js :async true"}]
        [:script {:src "https://cdn.jsdelivr.net/npm/echarts@5.4.1/dist/echarts.min.js" :async true}]
        [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.23.0/cytoscape.min.js" :async true}]
        [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/plotly.js/2.20.0/plotly.min.js" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js" :async true}]
        [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/3Dmol/1.5.3/3Dmol.min.js" :async true}]
        [:script {:src "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/leaflet-providers@2.0.0/leaflet-providers.min.js" :async true}]
        [:script {:src "https://unpkg.com/react@18/umd/react.production.min.js" :async true}]
        [:script {:src "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.js" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.cljs-ajax.js" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.reagent.js" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/d3-require@1" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.tmdjs.js" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.emmy.js" :async true}]
        [:script {:src "https://scicloj.github.io/scittle/js/scittle.mathbox.js" :async true}]
        [:script {:src "https://cdn.jsdelivr.net/npm/d3@7" :async true}]
        [:script {:src "https://code.jquery.com/jquery-3.6.0.min.js" :async true}]
        [:script {:src "https://code.jquery.com/ui/1.13.1/jquery-ui.min.js" :async true}]
        [:script {:src "https://code.highcharts.com/highcharts.js" :async true}]
        ]

       [:body.px-4
        [:ul.flex.flex-col.gap-2.v-full.p-1
         notebook-cells
         (add-row-button)]
        #_[:script
           {:type "text/javascript"
            :src
            "https://microsoft.github.io/monaco-editor/node_modules/monaco-editor/min/vs/loader.js"}]
        #_[:script
           "const opts = {
        language: 'clojure',
        lineNumbers: 'off',
        folding: false,
        glyphMargin: false,
        wordWrap: 'on',
        minimap: { enabled: false },
        multiCursorLimit: 1,
        links: false,
        overviewRulerLanes: 0,
        value: \"\"
      }
      require.config({ paths: { vs: 'https://microsoft.github.io/monaco-editor/node_modules/monaco-editor/min/vs' } });
      require(['vs/editor/editor.main'], function () {
        // var editor = monaco.editor.create(document.getElementById('editor'), opts);
      });"]]]])))