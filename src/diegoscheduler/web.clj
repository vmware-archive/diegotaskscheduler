(ns diegoscheduler.web
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop chan pipe tap mult dropping-buffer]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [org.httpkit.server :refer [run-server]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [diegoscheduler.diego :as d]))

(defn- log [msg]
  (println msg))

(defn- handle-new-tasks [new-tasks web-client]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (do
          (log "New task")
          (>! new-tasks message)))
      (recur))))

(defn- create-ws-handler [new-tasks client-pushes]
  (log (str "New WS chan: " client-pushes))
  (fn [{web-client :ws-channel}]
    (handle-new-tasks new-tasks web-client)
    (pipe client-pushes web-client)))

(defn- create-routes [new-tasks finished-tasks client-pushes]
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
               (put! finished-tasks parsed-task)
               {:status 200})
             (catch Exception e
               (log (str "Exception: " e))
               {:status 500})))
     (route/resources "/")
     (route/not-found "<h1>Page not found</h1>"))))

(defrecord WebServer [new-tasks finished-tasks client-pushes
                      port
                      server]
  component/Lifecycle
  (start [component]
    (let [routes (create-routes new-tasks finished-tasks client-pushes)
          server (run-server routes {:port port})]
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      component)))

(defn new-web-server [new-tasks finished-tasks client-pushes port]
  (map->WebServer {:new-tasks new-tasks
                   :finished-tasks finished-tasks
                   :client-pushes client-pushes
                   :port port}))
