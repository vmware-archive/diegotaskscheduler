(ns diegoscheduler.components.diego-updater
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! chan]]
            [diegoscheduler.diego :as diego]
            [overtone.at-at :as atat]))

(defrecord DiegoUpdater [sched-pool channel job period]
  component/Lifecycle
  (start [component]
    (let [channel (chan)
          sched-pool (atat/mk-pool)
          job (atat/every period
                          #(put! channel {:processing (diego/remote-tasks)})
                          sched-pool)]
      (assoc component
             :sched-pool sched-pool
             :channel channel
             :job job)))
  (stop [component]
    (when job (atat/stop job))
    component))

(defn new-diego-updater [period]
  (map->DiegoUpdater {:period period}))
