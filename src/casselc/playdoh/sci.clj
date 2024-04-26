(ns casselc.playdoh.sci
  (:require
   [sci.core :as sci]
   [clojure.tools.logging.readable :as logr]))

(set! *warn-on-reflection* true)

(let [ns-alias-map {#_#_'aerial.hanami.templates 'ht
                    #_#_'clojure.math 'math
                    #_#_'fastmath.stats 'fmstats
                    #_#_'scicloj.metamorph.core 'mm
                    #_#_'scicloj.metamorph.ml 'ml
                    #_#_'scicloj.metamorph.ml.classification nil
                    #_#_'scicloj.metamorph.ml.loss 'loss
                    #_#_'scicloj.metamorph.ml.toydata 'toydata
                    #_#_'scicloj.ml.smile.classification nil
                    #_#_'scicloj.ml.smile.regression nil
                    #_#_'scicloj.noj.v1.stats 'stats
                    #_#_'scicloj.noj.v1.vis.hanami 'hanami
                    'tablecloth.api 'tc
                    'tablecloth.column.api 'tcc
                    #_#_'tablecloth.pipeline 'tcpipe
                    'tech.v3.dataset 'ds
                    #_#_'tech.v3.dataset.categorical 'ds-cat
                    #_#_'tech.v3.dataset.column 'ds-col
                    #_#_'tech.v3.dataset.column-filters 'ds-cf
                    #_#_'tech.v3.dataset.io.csv 'ds-csv
                    #_#_'tech.v3.dataset.io.datetime 'ds-dt
                    #_#_'tech.v3.dataset.io.string-row-parser nil
                    #_#_'tech.v3.dataset.join 'ds-join
                    #_#_'tech.v3.dataset.math 'ds-math
                    #_#_'tech.v3.dataset.modelling 'ds-mod
                    #_#_'tech.v3.dataset.print nil
                    #_#_'tech.v3.dataset.reductions nil
                    #_#_'tech.v3.dataset.rolling nil
                    #_#_'tech.v3.dataset.set nil
                    #_#_'tech.v3.dataset.tensor nil
                    #_#_'tech.v3.dataset.zip nil}
      opts (delay
             (require 'casselc.playdoh.impl.sci-deps)
             (logr/info "Initializing SCI options map...")
             (let [namespaces (into {} (for [ns-sym (keys ns-alias-map)
                                             :let [sci-ns (sci/create-ns ns-sym)
                                                   publics (ns-publics ns-sym)
                                                   sci-ns (update-vals publics #(sci/copy-var* % sci-ns))]]
                                         [ns-sym sci-ns]))
                   aliases (into {} (for [[ns-sym alias-sym] (filter second ns-alias-map)
                                          :let [sci-ns (namespaces ns-sym)]]
                                      [alias-sym sci-ns]))]
               {:namespaces namespaces
                :aliases aliases}))]
  (defn init
    []
    (sci/init @opts)))

(defn evaluate-in-context
  [ctx form-str]
  (logr/info "Evaluating:" form-str)
  (logr/info "With context:" ctx)
  (logr/spyf :trace "And result: %s" (sci/eval-string* ctx form-str)))