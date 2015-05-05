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

(defn- handle-new-tasks [new-tasks web-client callback-url]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (let [{:keys [args guid dir domain docker-image
                      env path result-file]} message
              task (d/create-task {:guid guid
                                   :dir dir
                                   :domain domain
                                   :rootfs docker-image
                                   :path path
                                   :args args
                                   :env env
                                   :result-file result-file
                                   :callback-url callback-url})]
          (log (str "New task with guid " guid))
          (>! new-tasks task)))
      (recur))))

(defn- create-ws-handler [new-tasks client-pushes callback-url]
  (log (str "New WS chan: " client-pushes))
  (fn [{web-client :ws-channel}]
    (handle-new-tasks new-tasks web-client callback-url)
    (pipe client-pushes web-client)))

(defn- create-routes [new-tasks finished-tasks client-pushes callback-url]
  (let [updates-mult (mult client-pushes)]
    (routes
     (GET "/" [] (resource-response "index.html" {:root "public"}))
     (GET "/ws" []
          (log "Got /ws request")
          (-> (create-ws-handler new-tasks
                                 (tap updates-mult (chan (dropping-buffer 1)))
                                 callback-url)
              (wrap-websocket-handler)))
     (POST "/taskfinished" {body :body}
           (log "Task finished")
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
                      port callback-url
                      server]
  component/Lifecycle
  (start [component]
    (log (str "Using port " port))
    (let [routes (create-routes new-tasks finished-tasks
                                client-pushes callback-url)
          server (run-server routes {:port port})]
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      component)))

(defn new-web-server [new-tasks finished-tasks client-pushes port callback-url]
  (map->WebServer {:new-tasks new-tasks
                   :finished-tasks finished-tasks
                   :client-pushes client-pushes
                   :port port
                   :callback-url callback-url}))
