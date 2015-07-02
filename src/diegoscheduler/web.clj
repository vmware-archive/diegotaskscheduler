(ns diegoscheduler.web
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop chan pipe onto-chan]]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [org.httpkit.server :refer [run-server]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [sente-web-server-adapter]]
            [diegoscheduler.diego :as d]
            [diegoscheduler.pages :as pages])
  (:import java.util.UUID))

(defn- handle-new-tasks [new-tasks web-client]
  (go-loop []
    (when-let [{[id event-data] :event} (<! web-client)]
      (when (= "diegotaskscheduler" (namespace id))
        (let [{:keys [args dir domain docker-image env log-guid
                      path result-file quantity]} event-data
              tasks (for [_ (range (Integer. quantity))]
                      (d/create-task {:guid (str (UUID/randomUUID))
                                      :log_guid log-guid
                                      :dir dir
                                      :domain domain
                                      :rootfs docker-image
                                      :path path
                                      :args args
                                      :env env
                                      :result-file result-file}))]
          (log/info "User request for" quantity "tasks")
          (onto-chan new-tasks tasks false)))
      (recur))))

(defn- create-routes [new-tasks client-pushes ws-url]
  (let [{:keys [ch-recv send-fn
                ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (sente/make-channel-socket! sente-web-server-adapter {})]
    (handle-new-tasks new-tasks ch-recv)
    (go-loop []
      (when-let [task-update (<! client-pushes)]
        (send-fn :sente/all-users-without-uid task-update)
        (recur)))
    (routes
     (GET "/"      []    {:status 200 :body (pages/index {:ws-url ws-url})})
     (GET "/ws"  req   (ajax-get-or-ws-handshake-fn req))
     (POST "/ws" req   (ajax-post-fn req))
     (route/resources "/")
     (route/not-found "<h1>Page not found</h1>"))))

(defrecord WebServer [new-tasks client-pushes
                      port ws-url
                      server]
  component/Lifecycle
  (start [component]
    (log/info "Using port " port)
    (let [routes (-> (create-routes new-tasks client-pushes ws-url)
                     ring.middleware.keyword-params/wrap-keyword-params
                     ring.middleware.params/wrap-params)
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
