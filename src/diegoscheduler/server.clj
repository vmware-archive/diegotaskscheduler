(ns diegoscheduler.server
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! put! close! go-loop]])
  (:gen-class))

(defn ws-handler [{:keys [ws-channel] :as req}]
  (println "Opened connection from" (:remote-addr req))
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! ws-channel)]
      (prn "Message received:" msg)
      (>! ws-channel (if error
                       (format "Error: '%s'." (pr-str msg))
                       {:received (format "You passed: '%s' at %s." (pr-str message) (java.util.Date.))}))
      (recur))))

(defroutes app
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/ws" [] (-> ws-handler (wrap-websocket-handler)))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(defn -main []
  (http-kit/run-server app {:port 8080}))
