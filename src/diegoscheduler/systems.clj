(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan timeout split mult pipe tap]]
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
        tasks-ready-for-resubmission (chan)
        new-tasks-mult (mult new-tasks-input)
        user-submissions-for-diego (chan)
        user-submissions-for-resubmitter (chan) ; needed to resubmit original tasks
        tasks-from-diego-input (chan)
        tasks-from-diego-mult (mult tasks-from-diego-input)
        capacity-failures-from-diego (chan 1 (filter capacity-failure?))
        tasks-for-ui (chan)
        ui-updates (async/merge [tasks-for-ui])
        schedule (fn [] (timeout update-interval))]
    (tap new-tasks-mult user-submissions-for-diego)
    (tap new-tasks-mult user-submissions-for-resubmitter)
    (tap tasks-from-diego-mult capacity-failures-from-diego)
    (tap tasks-from-diego-mult tasks-for-ui)
    (pipe tasks-ready-for-resubmission new-tasks-input)
    (component/system-map
     :diego (new-diego user-submissions-for-diego
                       tasks-from-diego-input schedule
                       http/GET http/POST http/DELETE
                       api-url)
     :resubmitter (new-resubmitter capacity-failures-from-diego
                                   tasks-ready-for-resubmission
                                   user-submissions-for-resubmitter)
     :web (new-web-server new-tasks-input ui-updates port ws-url))))

(defn -main []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (log/error "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
