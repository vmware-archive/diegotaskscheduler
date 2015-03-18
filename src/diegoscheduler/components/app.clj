(ns diegoscheduler.components.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop go chan pipe]]
            [diegoscheduler.diego :as diego]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]))

(defn log [msg]
  (spit "log/server.log" (str msg "\n\n")))

(defn handle-incoming [ws-channel]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! ws-channel)]
      (if error
        (>! ws-channel {:error msg})
        (diego/create-task message))
      (recur))))

(defn create-ws-handler [down-ch]
  (fn [{:keys [ws-channel]}]
    (handle-incoming ws-channel)
    (pipe down-ch ws-channel)))

(defn resolve-task [m task]
  (-> m
      (update-in [:resolved] conj task)))

(defn create-routes [state down-ch]
  (routes
   (GET "/" [] (resource-response "index.html" {:root "public"}))
   (GET "/ws" [] (-> (create-ws-handler down-ch) (wrap-websocket-handler)))
   (POST "/taskfinished" {body :body}
         (log (str "Task finished"))
         (try
           (let [parsed-task (diego/parse-task (slurp body))
                 new-state (swap! state
                                  update-in [:tasks]
                                  resolve-task parsed-task)]
             (log (str "About to send\n" (:tasks new-state)))
             (put! down-ch {:tasks (:tasks new-state)})
             {:status 200})
           (catch Exception e
             (log (str "Exception: " e))
             {:status 500})))
   (route/resources "/")
   (route/not-found "<h1>Page not found</h1>")))

(defrecord App [updater]
  component/Lifecycle
  (start [component]
    (let [state (atom {:tasks {:resolved [] :processing []}})
          down-ch (chan)
          diego-updates-ch (:channel updater)
          routes (create-routes state down-ch)]
      (go-loop []
        (when-let [{:keys [processing]} (<! diego-updates-ch)]
          (>! down-ch {:tasks (:tasks (swap! state
                                             update-in [:tasks]
                                             assoc :processing processing))})
          (recur)))
      (assoc component :handler routes)))
  (stop [component]
    component))

(defn new-app []
  (map->App {}))
