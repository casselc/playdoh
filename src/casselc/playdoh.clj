(ns casselc.playdoh
  (:require
   [clojure.tools.logging.readable :as logr]
   [casselc.playdoh.server :as server])
  #_(:import (org.crac Core))
  (:gen-class))


(defn -main
  [& [arg]]
  (when arg
    (when (= "crac" arg)
      (logr/info "Loading SCI dependencies before CRAC checkpoint")
      (require 'casselc.playdoh.impl.sci-deps)
      (logr/info "Ready for checkpoint")
      #_(Core/checkpointRestore))
    (when-let [port (or (parse-long arg)
                        (parse-long (or (System/getenv "PLAYDOH_PORT") "")))]
      (logr/info "Starting on port" port)
      (server/start! port)
      (logr/info "Server started."))))

(comment
  (server/start! 8181)
  (server/stop!))