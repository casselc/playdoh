(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.casselc/playdoh)
(def version "0.1.0-SNAPSHOT")
(def main 'casselc.playdoh)
(def class-dir "target/classes")

(defn test "Run all the tests." [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis     basis
                   :main      'clojure.main
                   :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- uber-opts [{:keys [uber-file] :as opts}]
  (assoc opts
         :lib lib :main main
         :uber-file (or (some-> uber-file str) (format "target/%s-%s.jar" lib version))
         :basis (b/create-basis {})
         :class-dir class-dir
         :src-dirs ["src"]
         :ns-compile [main]))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  #_(test opts)
  (b/delete {:path "target"})
  (let [opts (update (uber-opts opts)
                     :ns-compile conj 'casselc.playdoh.impl.sci-deps)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR...")
    (b/uber opts))
  opts)
