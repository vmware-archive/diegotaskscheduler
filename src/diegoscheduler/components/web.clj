(ns diegoscheduler.components.web
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]))

(defrecord WebServer [port server app]
  component/Lifecycle
  (start [component]
    (let [server (run-server (:handler app) {:port port})]
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      component)))

(defn new-web-server [port]
  (map->WebServer {:port port}))
