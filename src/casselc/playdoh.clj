(ns casselc.playdoh
  (:require
   [clojure.tools.logging.readable :as logr]
   [casselc.playdoh.server :as server])
  (:gen-class))


(defn -main
  [& [arg]]
  (when arg 
    (when-let [port (or (parse-long arg)
                        (parse-long (or (System/getenv "PLAYDOH_PORT") "")))]
      (logr/info "Starting on port" port)
      (server/start! port)
      (logr/info "Server started."))))

(comment
  (server/start! 8181)
  (server/stop!))