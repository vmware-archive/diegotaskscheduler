(ns diegoscheduler.sente-interop
  #?(:cljs (:require [cljs.core.async :refer [chan]]
                     [diegoscheduler.tasks :as tasks]))
  #?(:clj (:require [clojure.core.async :refer [chan]]
                    [diegoscheduler.tasks :as tasks])))

(def extract-data
  (map (fn [{[_ [_ data]] :event}] data)))

(def events
  "Map of type keywords to channels that automatically extract the
  data portion of a sente event"
  (into {}
        (map (fn [type] {type (chan 1 extract-data)}))
        [:queued :running :successful :failed :rate :cell-quantity]))

(defn task-topic
  [{[_ [_ data]] :event}]
  (tasks/state-of data))

(defn app-topic
  [{[_ [event-type _]] :event :as e}]
  (case event-type
    :diegotaskscheduler/rate :rate
    :diegotaskscheduler/cell-quantity :cell-quantity
    (task-topic e)))

(defn topic
  "Return topic keyword for a given sente event"
  [{[id _] :event :as e}]
  (if (= :chsk/recv id)
    (app-topic e)
    :connection))
