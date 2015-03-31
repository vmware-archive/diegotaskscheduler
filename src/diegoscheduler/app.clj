(ns diegoscheduler.app
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! >! put! go-loop chan timeout
                                        tap mult dropping-buffer alt! split]]))

(defn- log [msg]
  (println msg))

(defn- resolve-task [m task]
  (update-in m [:resolved] conj task))

(defn- capacity-failure? [task]
  (= "insufficient resources" (task :failure_reason)))

(defrecord App [processing-tasks finished-tasks retry-tasks client-pushes
                retry-delay
                stopper]
  component/Lifecycle
  (start [component]
    (log "Starting new app")
    (let [stopper (chan)
          state (atom {:tasks {:resolved [] :processing []}})
          [capacity-failure-tasks
           resolving-tasks] (split capacity-failure? finished-tasks)]
      (go-loop []
        (alt!
          processing-tasks ([tasks _]
                            (>! client-pushes (swap! state
                                                     update-in [:tasks]
                                                     assoc :processing tasks))
                            (recur))
          resolving-tasks ([task _]
                           (swap! state
                                  update-in [:tasks]
                                  resolve-task task)
                           (recur))
          stopper :stopped))
      (go-loop []
        (when-let [task (<! capacity-failure-tasks)]
          (>! retry-tasks
              (assoc task
                     :task_guid (str "retry-"
                                     (task :task_guid))))
          (<! (timeout retry-delay))
          (recur)))
      (assoc component
             :state state
             :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-app [processing-tasks finished-tasks retry-tasks client-pushes
               retry-delay]
  (map->App {:processing-tasks processing-tasks
             :finished-tasks finished-tasks
             :retry-tasks retry-tasks
             :client-pushes client-pushes
             :retry-delay retry-delay}))
