(ns casselc.playdoh.impl.clay.util.claywalk)

;; A dataset-friendly version of clojure.walk

(defn walk
  [inner outer form]
  (-> (cond
        ;; Avoid walking into columns and datasets.
        (-> form class str
            (#{"class tech.v3.dataset.impl.column.Column"
               "class tech.v3.dataset.impl.dataset.Dataset"}))
        form
        ;; In other cases, mimic clojure.walk.
        (list? form)
        (outer (apply list (map inner form)))
        ;;
        (instance? clojure.lang.IMapEntry form)
        (outer (clojure.lang.MapEntry/create (inner (key form)) (inner (val form))))
        ;;
        (seq? form) (outer (doall (map inner form)))
        ;;
        (instance? clojure.lang.IRecord form)
        (outer (reduce (fn [r x] (conj r (inner x))) form form))
        ;;
        (coll? form)
        (outer (into (empty form) (map inner form)))
        ;;
        :else (outer form))))

(defn postwalk
  [f form]
  (walk (partial postwalk f) f form))

(defn prewalk
  [f form]
  (walk (partial prewalk f) identity (f form)))