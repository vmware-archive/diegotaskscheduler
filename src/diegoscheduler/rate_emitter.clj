(ns diegoscheduler.rate-emitter
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [alt! chan go-loop put! >!]]))

(defn rate
  [n coll]
  (let [total (->> coll reverse (take n) flatten set count)]
    (/ total n)))

(defn last-tick
  [coll]
  (dec (count coll)))

(defn add-completed
  [coll task]
  (update-in coll [(last-tick coll)]
             conj task))

(defrecord RateEmitter
    [completed-tasks rate-ch schedule window
     stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          completions (atom [[]])]
      (go-loop []
        (alt!
          completed-tasks ([task _]
                           (swap! completions add-completed task)
                           (recur))
          (schedule) ([_ _]
                      (>! rate-ch
                          (double (rate window @completions)))
                      (swap! completions conj [])
                      (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-rate-emitter [completed-tasks rate schedule window]
  (map->RateEmitter {:completed-tasks completed-tasks
                     :rate-ch rate
                     :schedule schedule
                     :window window}))
