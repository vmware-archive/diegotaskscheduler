(ns diegoscheduler.sente-interop
  #?(:cljs (:require [cljs.core.async :refer [chan]]))
  #?(:clj (:require [clojure.core.async :refer [chan]])))

(def extract-data
  (map (fn [{[_ [_ data]] :event}] data)))

(def events
  "Map of type keywords to channels that automatically extract the
  data portion of a sente event"
  (into {}
        (map (fn [type] {type (chan 1 extract-data)}))
        [:queued :running :successful :failed :rate :cell-quantity]))
