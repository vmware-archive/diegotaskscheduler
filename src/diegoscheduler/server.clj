(ns diegoscheduler.server
  (:require [clojure.core.async :refer [<! >! put! close! go-loop go chan]]
            [diegoscheduler.diego :as diego]
            [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [overtone.at-at :as atat]
   )
  (:gen-class))

(def state (atom {:tasks {:resolved []
                          :processing []}
                  :task-updates []
                  :task-update-job nil}))
(def downch (chan))
(defonce sched-pool (atat/mk-pool))

(defn log [msg]
  (spit "log/server.log" (str msg "\n\n")))

(defn new-task-update-job [ws-channel task]
  (atat/every 500
              (fn []
                (let [processing (diego/remote-tasks)]
                  (put! ws-channel
                        {:tasks (:tasks (swap! state
                                               update-in [:tasks]
                                               assoc :processing processing))})))
              sched-pool))

(defn store-task-update-job [state task job]
  (let [task-added (update-in state [:task-updates] conj task)]
    (assoc task-added :task-update-job job)))

(defn start-task-updates [ws-channel task]
  (when (empty? (:task-updates @state))
    (swap! state store-task-update-job task (new-task-update-job ws-channel task))))

(defn remove-task-update [coll task]
  (remove #(= (:task_guid task) (:task_guid %)) coll))

(defn stop-task-updates [task]
  (let [new-state (swap! state update-in [:task-updates] remove-task-update task)]
    (when empty? (:task-updates new-state)
          (atat/stop (:task-update-job new-state)))))

(defn handle-incoming [ws-channel]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! ws-channel)]
      (if error
        (>! ws-channel {:error msg})
        (let [task (diego/create-task message)]
          (start-task-updates ws-channel task)))
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
        (try
          (let [parsed-task (diego/parse-task (slurp body))
                new-state (swap! state
                                 update-in [:tasks]
                                 resolve-task parsed-task)]
            (stop-task-updates parsed-task)
            (put! downch {:tasks (:tasks new-state)})
            {:status 200})
          (catch Exception e
            (log (str "Exception: " e))
            {:status 500})))

  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(defn -main []
  (http-kit/run-server app {:port 8080}))
