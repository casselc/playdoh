(ns casselc.playdoh.sci
  (:require
   [sci.core :as sci]
   [clojure.tools.logging.readable :as logr]))

(set! *warn-on-reflection* true)

(let [ns-alias-map {'aerial.hanami.templates 'ht
                    'clojure.math 'math
                    'fastmath.stats 'fmstats
                    'scicloj.metamorph.core 'mm
                    'scicloj.metamorph.ml 'ml
                    'scicloj.metamorph.ml.classification nil
                    'scicloj.metamorph.ml.loss 'loss
                    'scicloj.metamorph.ml.toydata 'toydata
                    'scicloj.ml.smile.classification nil
                    'scicloj.ml.smile.regression nil
                    'scicloj.noj.v1.stats 'stats
                    'scicloj.noj.v1.vis.hanami 'hanami
                    'tablecloth.api 'tc
                    'tablecloth.column.api 'tcc
                    'tablecloth.pipeline 'tcpipe
                    'tech.v3.dataset 'ds
                    'tech.v3.dataset.categorical 'ds-cat
                    'tech.v3.dataset.column 'ds-col
                    'tech.v3.dataset.column-filters 'ds-cf
                    'tech.v3.dataset.io.csv 'ds-csv
                    'tech.v3.dataset.io.datetime 'ds-dt
                    'tech.v3.dataset.io.string-row-parser nil
                    'tech.v3.dataset.join 'ds-join
                    'tech.v3.dataset.math 'ds-math
                    'tech.v3.dataset.modelling 'ds-mod
                    'tech.v3.dataset.print nil
                    'tech.v3.dataset.reductions nil
                    'tech.v3.dataset.rolling nil
                    'tech.v3.dataset.set nil
                    'tech.v3.dataset.tensor nil
                    'tech.v3.dataset.zip nil}
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
  (logr/trace "With context:" ctx)
  (logr/spyf "And result: %s" (sci/eval-string* ctx form-str)))