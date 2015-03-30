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

(defn handle-new-tasks [new-tasks web-client]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (do
          (log "New task")
          (>! new-tasks message)))
      (recur))))

(defn create-ws-handler [new-tasks client-pushes]
  (log (str "New WS chan: " client-pushes))
  (fn [{web-client :ws-channel}]
    (handle-new-tasks new-tasks web-client)
    (pipe client-pushes web-client)))

(defn resolve-task [m task]
  (update-in m [:resolved] conj task))

(defn create-routes [state new-tasks client-pushes]
  (let [updates-mult (mult client-pushes)]
    (routes
     (GET "/" [] (resource-response "index.html" {:root "public"}))
     (GET "/ws" []
          (log (str "Got /ws request"))
          (-> (create-ws-handler new-tasks (tap updates-mult (chan (dropping-buffer 1))))
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

(defrecord App [new-tasks processing-tasks]
  component/Lifecycle
  (start [component]
    (log "Starting new app")
    (let [state (atom {:tasks {:resolved [] :processing []}})
          client-pushes (chan)
          routes (create-routes state new-tasks client-pushes)]
      (go-loop []
        (when-let [{:keys [processing]} (<! processing-tasks)]
          (>! client-pushes (swap! state
                                   update-in [:tasks]
                                   assoc :processing processing))
          (recur)))
      (assoc component
             :handler routes
             :state state)))
  (stop [component]
    component))

(defn new-app [new-tasks processing-tasks]
  (map->App {:new-tasks new-tasks
             :processing-tasks processing-tasks}))
