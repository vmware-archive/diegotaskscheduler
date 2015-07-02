(ns diegoscheduler.rate-emitter
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [alt! chan go-loop put! >!]]))

(defn last-tick
  [coll]
  (dec (count coll)))

(defn inc-completed
  [coll]
  (update-in coll [(last-tick coll)] inc))

(defn add-tick
  [coll]
  (conj coll 0))

(defrecord RateEmitter
    [completed-tasks rate schedule window
     stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          completions (atom [0])]
      (go-loop []
        (alt!
          completed-tasks ([task _]
                           (swap! completions inc-completed)
                           (recur))
          (schedule) ([_ _]
                      (let [coll @completions
                            completions-in-window (nthrest coll
                                                           (- (count coll)
                                                              window))]
                        (>! rate
                            (double
                             (/ (apply + completions-in-window)
                                window)))

                        (swap! completions add-tick))
                      (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-rate-emitter [completed-tasks rate schedule window]
  (map->RateEmitter {:completed-tasks completed-tasks
                     :rate rate
                     :schedule schedule
                     :window window}))
