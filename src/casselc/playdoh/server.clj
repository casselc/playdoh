(ns casselc.playdoh.server
  (:require
   [clojure.string :as str]
   [clojure.tools.logging.readable :as logr]
   [dev.onionpancakes.chassis.core :as c]
   [org.httpkit.server :as http]
   [casselc.playdoh.sci :as sci]
   [casselc.playdoh.ui :as ui])
  (:import
   (java.net URLDecoder)))

(set! *warn-on-reflection* true)

(def kinds (delay (logr/info "kinds was forced") (into {} (requiring-resolve 'casselc.playdoh.ui/kinds))))

(def sessions (atom {}))

(def session-cookie-name (if (= "1" (System/getenv "PRODUCTION"))
                           "__Host-playdoh-sessionId"
                           "playdoh-sessionId"))

(defn session-cookie-header
  [^String session-id]
  (str session-cookie-name "=" session-id "; Secure; HttpOnly; Path=/; SameSite=Strict"))

(defn empty-cell
  []
  {:id (str (random-uuid))
   :text ""})

(defn new-cell!
  [session-id]
  (let [{:keys [id] :as cell} (empty-cell)
        sessions (swap! sessions
                        update session-id
                        (fn [{:keys [cells cell-list] :as session}]
                          (assoc session
                                 :cells (assoc cells id cell)
                                 :cell-list (conj cell-list id))))]
    (sessions session-id)))

(defn update-cell!
  [session-id {:keys [id] :as cell}]
  (let [sessions (swap! sessions
                        assoc-in [session-id :cells id] cell)]
    (sessions session-id)))

(defn reset-session!
  [session-id]
  (let [{:keys [id] :as cell} (empty-cell)
        sessions (swap! sessions
                        assoc session-id
                        {:context (sci/init)
                         :cells {id cell}
                         :cell-list [id]})]
    (sessions session-id)))

(defn reset-context!
  [session-id]
  (let [sessions (swap! sessions
                        update session-id
                        (fn [{:keys [cells]}]
                          {:context (sci/init)
                           :cells (mapv #(dissoc % :result) cells)}))]
    (sessions session-id)))

(defn new-session!
  []
  (let [id (str (random-uuid))]
    (logr/trace "Creating new session with id:" id)
    (reset-session! id)
    id))

(defn get-session-id
  [{:keys [headers] :as _req}]
  (if-let [^String raw-str (headers "cookie")]
    (let [kv-strs (str/split raw-str #";")
          cookies (->> kv-strs
                       (mapcat #(str/split % #"="))
                       (map str/trim)
                       (apply hash-map))]
      (or (cookies session-cookie-name) (new-session!)))
    (new-session!)))

(defn get-form-values
  [{:keys [content-type character-encoding body] :as _req}]
  (when (= "application/x-www-form-urlencoded" content-type)
    (let [body-str (slurp body)
          kv-strs (str/split body-str #"&")
          params (->> kv-strs
                      (mapcat #(str/split % #"="))
                      (map str/trim)
                      (apply hash-map))]
      (update-vals params #(URLDecoder/decode ^String % ^String character-encoding)))))

(defn is-htmx?
  [{:keys [headers] :as _req}]
  (parse-boolean (headers "hx-request" "false")))

(let [index-page ui/index-page
      result-pane ui/result-pane
      notebook-cell ui/notebook-cell
      add-row-button  ui/add-row-button]
  (defn router
    [{:keys [request-method uri]
      :as req}]
    (try
      (let [session-id (get-session-id req)
            {:keys [cell-list cells context]} (@sessions session-id)]
        (case [request-method uri]
          ([:get "/"]
           [:get "/index.html"]) {:status 200
                                  :headers {"Content-Type" "text/html"
                                            "Set-Cookie" (session-cookie-header session-id)}
                                  :body (c/html (index-page cell-list  cells))}
          [:post "/cell"] (let [{:keys [cells cell-list]} (new-cell! session-id)]
                            (if (is-htmx? req)
                              (let [last-cell (cells (last cell-list))]
                                {:status 200
                                 :headers {"Content-Type" "text/html"}
                                 :body (c/html [(notebook-cell last-cell)
                                                (add-row-button)])})
                              {:status 200
                               :headers {"Content-Type" "text/html"}
                               :body (c/html (index-page cell-list  cells))}))
          [:post "/evaluate"] (let [{:strs [id text kind] :as all} (get-form-values req)
                                    result (sci/evaluate-in-context context text)
                                    new-cell {:id id :text text :result result :kind (@kinds kind)}
                                    {:keys [cell-list cells]} (update-cell! session-id new-cell)]
                                (if (is-htmx? req)
                                  {:status 200
                                   :headers {"Content-Type" "text/html"}
                                   :body (c/html (result-pane new-cell))}
                                  {:status 200
                                   :headers {"Content-Type" "text/html"}
                                   :body (c/html (index-page cell-list cells))}))
          {:status 404
           :body (str uri " not found")}))
      (catch Exception e
        (logr/error e "Error processing request:\n" req)
        {:status 500
         :headers {"Content-Type" "text/plain"}
         :body (str "Oops, something broke:\n" (ex-message e))}))))

(def running-server (atom nil))

(defn start!
  [port] 
  (if-let [server @running-server]
    server
    (let [server (reset! running-server (http/run-server #'router {:port port :legacy-return-value? false}))]
      server)))

(defn stop!
  []
  (when-let [server @running-server]
    (http/server-stop! server)))