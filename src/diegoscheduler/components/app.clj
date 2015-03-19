(ns diegoscheduler.components.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop go chan pipe tap mult]]
            [diegoscheduler.diego :as diego]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]))

(defn log [msg]
  (spit "log/server.log" (str msg "\n\n")))

(defn handle-new-tasks [web-client]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (diego/create-task message))
      (recur))))

(defn create-ws-handler [diego-updates]
  (fn [{web-client :ws-channel}]
    (handle-new-tasks web-client)
    (pipe diego-updates web-client)))

(defn resolve-task [m task]
  (-> m (update-in [:resolved] conj task)))

(defn create-routes [state diego-updates]
  (let [updates-mult (mult diego-updates)]
    (routes
     (GET "/" [] (resource-response "index.html" {:root "public"}))
     (GET "/ws" []
          (-> (create-ws-handler (tap updates-mult (chan)))
              (wrap-websocket-handler)))
     (POST "/taskfinished" {body :body}
           (log (str "Task finished"))
           (try
             (let [parsed-task (diego/parse-task (slurp body))]
               (swap! state
                      update-in [:tasks]
                      resolve-task parsed-task)
               {:status 200})
             (catch Exception e
               (log (str "Exception: " e))
               {:status 500})))
     (route/resources "/")
     (route/not-found "<h1>Page not found</h1>"))))

(defrecord App [updater]
  component/Lifecycle
  (start [component]
    (let [state (atom {:tasks {:resolved [] :processing []}})
          diego-updates (chan)
          routes (create-routes state diego-updates)]
      (go-loop []
        (when-let [{:keys [processing]} (<! (:channel updater))]
          (>! diego-updates (swap! state
                                   update-in [:tasks]
                                   assoc :processing processing))
          (recur)))
      (assoc component :handler routes)))
  (stop [component]
    component))

(defn new-app []
  (map->App {}))
