(ns diegoscheduler.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop go chan pipe tap mult dropping-buffer]]
            [diegoscheduler.diego :as d]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]))

(defn log [msg]
  (println msg))

(defn handle-new-tasks [diego web-client]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (do
          (log "New task")
          (d/create-task diego message)))
      (recur))))

(defn create-ws-handler [diego diego-updates]
  (log (str "New WS chan: " diego-updates "\nCallback URL: " (:callback-url diego)))
  (fn [{web-client :ws-channel}]
    (handle-new-tasks diego web-client)
    (pipe diego-updates web-client)))

(defn resolve-task [m task]
  (-> m (update-in [:resolved] conj task)))

(defn create-routes [diego state diego-updates]
  (let [updates-mult (mult diego-updates)]
    (routes
     (GET "/" [] (resource-response "index.html" {:root "public"}))
     (GET "/ws" []
          (log (str "Got /ws request"))
          (-> (create-ws-handler diego (tap updates-mult (chan (dropping-buffer 1))))
              (wrap-websocket-handler)))
     (POST "/taskfinished" {body :body}
           (log (str "Task finished"))
           (try
             (let [parsed-task (d/parse-task (slurp body))]
               (swap! state
                      update-in [:tasks]
                      resolve-task parsed-task)
               {:status 200})
             (catch Exception e
               (log (str "Exception: " e))
               {:status 500})))
     (route/resources "/")
     (route/not-found "<h1>Page not found</h1>"))))

(defrecord App [diego]
  component/Lifecycle
  (start [component]
    (log "Starting new app")
    (let [state (atom {:tasks {:resolved [] :processing []}})
          diego-updates (chan)
          routes (create-routes diego state diego-updates)]
      (go-loop []
        (when-let [{:keys [processing]} (<! (:channel diego))]
          (>! diego-updates (swap! state
                                   update-in [:tasks]
                                   assoc :processing processing))
          (recur)))
      (assoc component
             :handler routes
             :state state)))
  (stop [component]
    component))

(defn new-app []
  (map->App {}))
