(ns diegoscheduler.components.diego-updater
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! <! chan timeout alt! go-loop]]
            [diegoscheduler.diego :as diego]))

(defrecord DiegoUpdater [sched-pool channel stopper period]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          processing-tasks (chan)]
      (go-loop []
        (alt!
          (timeout period) (do
                             (>! processing-tasks {:processing (diego/remote-tasks)})
                             (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper
             :channel processing-tasks)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego-updater [period]
  (map->DiegoUpdater {:period period}))
