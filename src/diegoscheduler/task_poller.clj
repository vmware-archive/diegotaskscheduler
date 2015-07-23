(ns diegoscheduler.task-poller
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! <! >! chan alt! go-loop onto-chan]]
            [clojure.tools.logging :as log]
            [diegoscheduler.diego :as d]))

(defrecord TaskPoller [tasks-from-diego schedule
                       getfn deletefn
                       api-url
                       stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (let [tasks (d/remote-resources "tasks" component)]
                        (d/delete-completed component tasks)
                        (doseq [t tasks]
                          (log/info (:state t) (:task_guid t)))
                        (onto-chan tasks-from-diego tasks false)
                        (recur)))
          stopper :stopped))

      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-task-poller [tasks-from-diego schedule
                       getfn deletefn
                       api-url]
  (map->TaskPoller {:tasks-from-diego tasks-from-diego
                    :schedule schedule
                    :getfn getfn
                    :deletefn deletefn
                    :api-url api-url}))
