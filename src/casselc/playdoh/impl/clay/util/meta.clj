(ns casselc.playdoh.impl.clay.util.meta)

(defn pr-str-with-meta [value]
  (binding [*print-meta* true]
    (pr-str value)))