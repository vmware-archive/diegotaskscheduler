(ns diegoscheduler.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop go chan pipe tap mult dropping-buffer alt!]]))

(defn- log [msg]
  (println msg))

(defn- resolve-task [m task]
  (update-in m [:resolved] conj task))

(defrecord App [new-tasks processing-tasks finished-tasks client-pushes
                stopper]
  component/Lifecycle
  (start [component]
    (log "Starting new app")
    (let [stopper (chan)
          state (atom {:tasks {:resolved [] :processing []}})]
      (go-loop []
        (alt!
          processing-tasks ([tasks _]
                            (>! client-pushes (swap! state
                                                     update-in [:tasks]
                                                     assoc :processing tasks))
                            (recur))
          finished-tasks ([task _]
                          (swap! state
                                 update-in [:tasks]
                                 resolve-task task)
                          (recur))
          stopper :stopped))
      (assoc component
             :state state
             :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-app [processing-tasks finished-tasks retry-tasks client-pushes]
  (map->App {:processing-tasks processing-tasks
             :finished-tasks finished-tasks
             :client-pushes client-pushes}))
