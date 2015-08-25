(ns diegoscheduler.task-poller
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! chan alt! go-loop onto-chan]]
            [clojure.tools.logging :as log]))

(defrecord TaskPoller
    [tasks-from-diego schedule getfn api-url stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          all-tasks (chan)]
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (getfn (str api-url "/tasks") all-tasks)
                      (recur))
          all-tasks  ([tasks _]
                      (onto-chan tasks-from-diego tasks
                                 false)
                      (recur))
          stopper    :stopped))

      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-task-poller [tasks-from-diego schedule getfn api-url]
  (map->TaskPoller {:tasks-from-diego tasks-from-diego
                    :schedule schedule
                    :getfn getfn
                    :api-url api-url}))
