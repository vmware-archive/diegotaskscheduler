(ns diegoscheduler.web
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop chan pipe tap mult dropping-buffer]]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [org.httpkit.server :refer [run-server]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [diegoscheduler.diego :as d]
            [diegoscheduler.pages :as pages])
  (:import java.util.UUID))

(defn- handle-new-tasks [new-tasks web-client]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! web-client)]
      (if error
        (>! web-client {:error msg})
        (let [{:keys [args dir domain docker-image env path result-file]} message
              guid (str (UUID/randomUUID))
              task (d/create-task {:guid guid
                                   :dir dir
                                   :domain domain
                                   :rootfs docker-image
                                   :path path
                                   :args args
                                   :env env
                                   :result-file result-file})]
          (log/info "User task request with guid " guid)
          (>! new-tasks task)))
      (recur))))

(defn- create-ws-handler [new-tasks client-pushes]
  (log/info "New WS chan: " client-pushes)
  (fn [{web-client :ws-channel}]
    (handle-new-tasks new-tasks web-client)
    (pipe client-pushes web-client)))

(defn- create-routes [new-tasks client-pushes ws-url]
  (let [updates-mult (mult client-pushes)]
    (routes
     (GET "/" [] {:status 200 :body (pages/index {:ws-url ws-url})})
     (GET "/ws" []
          (log/info "Got /ws request")
          (let [c (chan)]
            (tap updates-mult c)
            (-> (create-ws-handler new-tasks c)
                (wrap-websocket-handler))))
     (route/resources "/")
     (route/not-found "<h1>Page not found</h1>"))))

(defrecord WebServer [new-tasks client-pushes
                      port ws-url
                      server]
  component/Lifecycle
  (start [component]
    (log/info "Using port " port)
    (let [routes (create-routes new-tasks client-pushes ws-url)
          server (run-server routes {:port port})]
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      component)))

(defn new-web-server [new-tasks client-pushes port ws-url]
  (map->WebServer {:new-tasks new-tasks
                   :client-pushes client-pushes
                   :port port
                   :ws-url ws-url}))
