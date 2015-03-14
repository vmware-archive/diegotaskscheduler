(ns diegoscheduler.server
  (:require [diegoscheduler.diego :as diego]
            [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! put! close! go-loop go chan]])
  (:gen-class))

(def tasks (atom {:resolved []
                  :pending []
                  :processing []}))
(def downch (chan))

(defn handle-incoming [ws-channel]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! ws-channel)]
      (if error
        (>! ws-channel {:error msg})
        (do
          (diego/create-task message)
          (>! ws-channel {:tasks (swap! tasks assoc :processing (diego/remote-tasks))})))
      (recur))))

(defn handle-outgoing [ws-channel]
  (go-loop []
    (when-let [msg (<! downch)]
      (>! ws-channel msg)
      (recur))))

(defn ws-handler [{:keys [ws-channel] :as req}]
  (handle-incoming ws-channel)
  (handle-outgoing ws-channel))

(defn remove-task [coll task]
  (remove #(= (:task_guid task) (:task_guid %)) coll))

(defn resolve-task [m task]
  (-> m
      (update-in [:resolved] conj task)
      (update-in [:processing] remove-task task)))

(defroutes app
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/ws" [] (-> ws-handler (wrap-websocket-handler
                                {:read-ch (chan nil)
                                 :write-ch (chan nil)})))
  (POST "/taskfinished" {body :body}
        (let [parsed-task (diego/parse-task (slurp body))]
          (put! downch
                {:tasks (swap! tasks resolve-task parsed-task)})
          {:status 200}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(defn -main []
  (http-kit/run-server app {:port 8080}))
