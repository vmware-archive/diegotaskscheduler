(ns diegoscheduler.task-poller
  (:require [com.stuartsierra.component :as component]
            [clojure.set :refer [select]]
            [clojure.core.async :refer [put! chan alt! go-loop onto-chan pipeline]]
            [clojure.tools.logging :as log]))

(defn- remove-dupes
  [db]
  (filter (fn [task]
            (if (@db task)
              false
              (do (swap! db conj task)
                  true)))))

(defn- expire-tasks
  [db current-time-millis task-lifespan-millis]
  (select (fn [{task-time-micros :created_at}]
            (let [task-time-millis (/ task-time-micros 1000)]
              (< (- current-time-millis task-lifespan-millis)
                 task-time-millis)))
          db))

(defrecord TaskPoller
    [deduped-tasks schedule clock task-lifespan-millis getfn api-url stopper]
  component/Lifecycle
  (start [component]
    (let [db               (atom #{})
          stopper          (chan)
          all-tasks        (chan)
          tasks-from-diego (chan)]
      (pipeline 1
                deduped-tasks
                (remove-dupes db) tasks-from-diego
                false)
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (let [time-millis (clock)]
                        (log/info "DB:"
                                  (swap! db expire-tasks time-millis task-lifespan-millis))
                        (getfn (str api-url "/tasks") all-tasks)
                        (recur)))
          all-tasks  ([tasks _]
                      (onto-chan tasks-from-diego tasks false)
                      (recur))
          stopper    :stopped))

      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-task-poller [deduped-tasks schedule clock task-lifespan-millis getfn api-url]
  (map->TaskPoller {:deduped-tasks deduped-tasks
                    :schedule schedule
                    :clock clock
                    :task-lifespan-millis task-lifespan-millis
                    :getfn getfn
                    :api-url api-url}))
