(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan timeout split pipeline mult tap]]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]
            [diegoscheduler.resubmitter :refer [new-resubmitter]]
            [diegoscheduler.http :as http])
  (:gen-class))

(def ^:private update-interval 500)

(defn- capacity-failure? [t]
  (= "insufficient resources" (:failure_reason t)))

(defn main-system [port-str api-url ws-url]
  (let [port (Integer. port-str)
        new-tasks-input (chan)
        new-tasks-mult (mult new-tasks-input)
        diego-task-reader (chan)
        resubmitter-task-reader (chan)
        tasks-from-diego (chan)
        [tasks-for-resubmission tasks-for-ui] (split capacity-failure? tasks-from-diego)
        schedule (fn [] (timeout update-interval))]
    (tap new-tasks-mult diego-task-reader false)
    (tap new-tasks-mult resubmitter-task-reader false)
    (component/system-map
     :diego (new-diego diego-task-reader
                       tasks-from-diego schedule
                       http/GET http/POST http/DELETE
                       api-url)
     :resubmitter (new-resubmitter tasks-for-resubmission
                                   new-tasks-input
                                   resubmitter-task-reader)
     :web (new-web-server new-tasks-input tasks-for-ui port ws-url))))

(defn -main []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (log/error "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
