(ns diegoscheduler.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop chan timeout
                                        tap mult dropping-buffer alt! split]]))

(defn- log [msg]
  (println msg))

(defrecord App [tasks-from-diego client-pushes stopper]
  component/Lifecycle
  (start [component]
    (log "Starting new app")
    (let [stopper (chan)
          state (atom {:tasks []})]
      (go-loop []
        (alt!
          tasks-from-diego ([tasks _]
                            (>! client-pushes (swap! state
                                                     assoc :tasks tasks))
                            (recur))
          stopper :stopped))
      (assoc component
             :state state
             :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-app [tasks-from-diego client-pushes]
  (map->App {:tasks-from-diego tasks-from-diego
             :client-pushes client-pushes}))
