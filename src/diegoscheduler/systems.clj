(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan timeout split mult tap]]
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
        tasks-from-diego-input (chan)
        tasks-from-diego-mult (mult tasks-from-diego-input)
        tasks-for-resubmission (chan 1 (filter capacity-failure?))
        tasks-for-ui (chan)
        ui-updates (async/merge [tasks-for-ui])
        schedule (fn [] (timeout update-interval))]
    (tap new-tasks-mult diego-task-reader false)
    (tap new-tasks-mult resubmitter-task-reader false)
    (tap tasks-from-diego-mult tasks-for-resubmission)
    (tap tasks-from-diego-mult tasks-for-ui)
    (component/system-map
     :diego (new-diego diego-task-reader
                       tasks-from-diego-input schedule
                       http/GET http/POST http/DELETE
                       api-url)
     :resubmitter (new-resubmitter tasks-for-resubmission
                                   new-tasks-input
                                   resubmitter-task-reader)
     :web (new-web-server new-tasks-input ui-updates port ws-url))))

(defn -main []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (log/error "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
