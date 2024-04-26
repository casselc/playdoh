(ns casselc.playdoh.impl.sci-deps
  (:require [clojure.tools.logging.readable :as logr])
  #_(:require
     [aerial.hanami.templates]
     [clojure.math]
     [fastmath.stats]
     [scicloj.metamorph.core]
     [scicloj.metamorph.ml]
     [scicloj.metamorph.ml.classification]
     [scicloj.metamorph.ml.loss]
     [scicloj.metamorph.ml.toydata]
     [scicloj.ml.smile.classification]
     [scicloj.ml.smile.regression]
     [scicloj.noj.v1.stats]
     [scicloj.noj.v1.vis.hanami]
     [tablecloth.api]
     [tablecloth.column.api]
     [tablecloth.pipeline]
     [tech.v3.dataset]
     [tech.v3.dataset.categorical]
     [tech.v3.dataset.column]
     [tech.v3.dataset.column-filters]
     [tech.v3.dataset.io.csv]
     [tech.v3.dataset.io.datetime]
     [tech.v3.dataset.io.string-row-parser]
     [tech.v3.dataset.join]
     [tech.v3.dataset.math]
     [tech.v3.dataset.modelling]
     [tech.v3.dataset.print]
     [tech.v3.dataset.reductions]
     [tech.v3.dataset.rolling]
     [tech.v3.dataset.set]
     [tech.v3.dataset.tensor]
     [tech.v3.dataset.zip]))

(logr/info "Loading SCI context namespaces")

(doseq [ns ['aerial.hanami.templates
            'clojure.math
            'fastmath.stats
            'scicloj.metamorph.core
            'scicloj.metamorph.ml
            'scicloj.metamorph.ml.classification
            'scicloj.metamorph.ml.loss
            'scicloj.metamorph.ml.toydata
            'scicloj.ml.smile.classification
            'scicloj.ml.smile.regression
            'scicloj.noj.v1.stats
            'scicloj.noj.v1.vis.hanami
            'tablecloth.api
            'tablecloth.column.api
            'tablecloth.pipeline
            'tech.v3.dataset
            'tech.v3.dataset.categorical
            'tech.v3.dataset.column
            'tech.v3.dataset.column-filters
            'tech.v3.dataset.io.csv
            'tech.v3.dataset.io.datetime
            'tech.v3.dataset.io.string-row-parser
            'tech.v3.dataset.join
            'tech.v3.dataset.math
            'tech.v3.dataset.modelling
            'tech.v3.dataset.print
            'tech.v3.dataset.reductions
            'tech.v3.dataset.rolling
            'tech.v3.dataset.set
            'tech.v3.dataset.tensor
            'tech.v3.dataset.zip]]
  (logr/trace "Requiring" (require ns)))

(logr/info "Loaded dependencies")