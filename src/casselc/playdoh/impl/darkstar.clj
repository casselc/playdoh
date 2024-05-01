(ns casselc.playdoh.impl.darkstar
  (:require [clojure.core :as core]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as logr])
  (:import (java.util.function Consumer Predicate)
           (org.graalvm.polyglot Context HostAccess)))


(def ^:dynamic *base-directory* nil)

(defn read-file
  "A very, very slight polyfill for Node's fs.readFile that uses `*base-directory*` as Vega's idea of current working directory."
  [filename]
  ;; TODO only system error handling!
  (slurp (.getAbsolutePath (java.io.File. (str *base-directory* filename)))))

(def engine
  (let [t (reify Predicate
            (test [_ _] true))
        ctx (-> (Context/newBuilder (into-array String ["js"]))
                (.allowHostAccess HostAccess/ALL)
                (.allowHostClassLookup t)
                (.allowExperimentalOptions true)
                (.option "js.esm-eval-returns-exports" "true")
                .build)]
    (doto ctx

      (.eval "js" "
async function fetch(path, options) {
  var body = Java.type('clojure.core$slurp').invokeStatic(path,null);
  return {'ok' : true,
          'body' : body,
          'text' : (function() {return body;}),
          'json' : (function() {return JSON.parse(body);})};
}
function readFile(path, callback) {
  try {
    var data = Java.type('applied_science.darkstar$read_file').invokeStatic(path);
    callback(null, data);
  } catch (err) {
    printErr(err);
  }
}
var fs = {'readFile':readFile};  
                   ")
      (.eval "js" (slurp (io/resource "vega.js")))
      (.eval "js" (slurp (io/resource "vega-lite.js"))))))

(let [f (.eval engine "js" "vlSpec => JSON.stringify(vegaLite.compile(JSON.parse(vlSpec)).spec);")]
  (defn vega-lite->vega
    "Converts a VegaLite spec into a Vega spec."
    [spec]
    (logr/infof "Sending:\n:%s\n to JS engine." spec)
    (->> [spec]
         (into-array String)
         (.execute f)
         .asString)))

(let [f (.eval engine "js" "spec => new vega.View(vega.parse(JSON.parse(spec)), {renderer:'svg'}).finalize();")]
  (defn vega-spec->view
    "Converts a Vega spec into a Vega view object, finalizing all resources."
    [spec]
    (->> [spec]
         (into-array String)
         (.execute f))))

(let [f (.eval engine "js" "async view => {
  try {
    return await view.toSVG(1.0)
  } catch (e) {
    return `<svg><text>${e}</text></svg>`
  }
}")]
  (defn view->svg
    "Converts a Vega view object into an SVG."
    [view]
    (let [p (promise)
          resolver (reify Consumer
                     (accept [_ v]
                       (println "Delivering" v)
                       (deliver p v)))
          js-promise (->> [view]
                          to-array
                          (.execute f))]
      (println "Invoking JS")
      (future
        (.invokeMember js-promise "then" (to-array [resolver])))
      p)))

(defn vega-spec->svg
  "Calls Vega to render the spec in `vega-spec-json-string` to the SVG described by that spec."
  [vega-spec-json-string]
  @(view->svg (vega-spec->view vega-spec-json-string)))

(defn vega-lite-spec->svg
  "Converts `vega-lite-spec-json-string` to a full Vega spec, then uses Vega to render the SVG described by that spec."
  [vega-lite-spec-json-string] 
  (vega-spec->svg (vega-lite->vega vega-lite-spec-json-string)))

(comment

  (->> (slurp "test/applied_science/vega-specs/airports.vg.json")
       vega-spec->svg
       (spit "vl-movies.svg")))
