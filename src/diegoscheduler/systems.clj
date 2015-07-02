(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan timeout split mult pipe tap]]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]
            [diegoscheduler.rate-emitter :refer [new-rate-emitter]]
            [diegoscheduler.resubmitter :refer [new-resubmitter]]
            [diegoscheduler.http :as http])
  (:gen-class))

(def update-interval
  "Poll Diego at this interval, in ms."
  500)

(def rate-denominator
  "Calculate rate as tasks per this many ms.
  NB: The display of this denominator is duplicated in frontend."
  1000)

(def rate-window
  "Take a rolling average inside this many rate-denominators."
  20)

(defn- capacity-failure?
  [t]
  (= "insufficient resources" (:failure_reason t)))

(defn- completed?
  [t]
  (comp (= "COMPLETED" (:status t))
        (complement capacity-failure?)))

(def set-as-queued
  (map #(merge % {:state "QUEUED"})))

(defn- tag-all
  [tag]
  (map (fn [t] [tag t])))

(defn main-system
  [port-str api-url ws-url]
  (let [port (Integer. port-str)
        new-tasks-input (chan)
        new-tasks-mult (mult new-tasks-input)

        tasks-ready-for-resubmission (chan)
        tasks-ready-for-resubmission-mult (mult tasks-ready-for-resubmission)
        resubmit-as-new-tasks (chan)
        tasks-enqueued (chan 1 (comp set-as-queued
                                     (tag-all :diegotaskscheduler/task)))

        user-submissions-for-diego (chan)
        user-submissions-for-resubmitter (chan) ; resubmitter needs to look up original tasks

        tasks-from-diego-input (chan)
        tasks-from-diego-mult (mult tasks-from-diego-input)
        capacity-failures-from-diego (chan 1 (filter capacity-failure?))

        completed-tasks (chan 1 (filter completed?))
        rate-of-completion (chan 1 (tag-all :diegotaskscheduler/rate))

        tasks-for-ui (chan 1 (tag-all :diegotaskscheduler/task))
        ui-updates (async/merge [tasks-for-ui tasks-enqueued rate-of-completion])

        poll-schedule (fn [] (timeout update-interval))
        rate-schedule (fn [] (timeout rate-denominator))]

    (tap new-tasks-mult user-submissions-for-diego)
    (tap new-tasks-mult user-submissions-for-resubmitter)

    (tap tasks-from-diego-mult capacity-failures-from-diego)
    (tap tasks-from-diego-mult completed-tasks)
    (tap tasks-from-diego-mult tasks-for-ui)

    (tap tasks-ready-for-resubmission-mult tasks-enqueued)
    (tap tasks-ready-for-resubmission-mult resubmit-as-new-tasks)
    (pipe resubmit-as-new-tasks new-tasks-input)

    (component/system-map
     :diego (new-diego user-submissions-for-diego
                       tasks-from-diego-input poll-schedule
                       http/GET http/POST http/DELETE
                       api-url)
     :resubmitter (new-resubmitter capacity-failures-from-diego
                                   tasks-ready-for-resubmission
                                   user-submissions-for-resubmitter)
     :rate-emitter (new-rate-emitter completed-tasks rate-of-completion rate-schedule rate-window)
     :web (new-web-server new-tasks-input ui-updates port ws-url))))

(defn -main
  []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (log/error "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
