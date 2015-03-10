(ns diegoscheduler.server
  (:require [diegoscheduler.diego :as diego]
            [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! put! close! go-loop go chan]])
  (:gen-class))

(def tasks (atom {:resolved []}))
(def downch (chan))

(defn ws-handler [{:keys [ws-channel] :as req}]
  (go-loop []
    (when-let [{:keys [message error] :as msg} (<! ws-channel)]
      (>! ws-channel (if error
                       (format "Error: '%s'." (pr-str msg))
                       (diego/create-task message)
))
      (recur)))
  (go-loop []
    (when-let [msg (<! downch)]
      (>! ws-channel msg))))

(defroutes app
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/ws" [] (-> ws-handler (wrap-websocket-handler)))
  (POST "/taskfinished" {body :body}
        (if body
          (let [raw-task (slurp body)]
            (swap! tasks (fn [m] (update-in m [:resolved] #(conj % raw-task))))
            (put! downch {:tasks @tasks})
            {:status 200})
          {:status 400}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(defn -main []
  (http-kit/run-server app {:port 8080}))
