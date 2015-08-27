(ns diegoscheduler.task-submitter
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go-loop <! chan]]
            [clojure.tools.logging :as log]))

(defrecord TaskSubmitter
    [new-tasks postfn api-url]
  component/Lifecycle
  (start [component]
    (go-loop []
      (when-let [task (<! new-tasks)]
        (if (:task_guid task)
          (do
            (log/info "POSTing to diego:" (:task_guid task))
            (postfn (str api-url "/tasks") task (chan)))
          (log/error "Got a bad task:" task))
        (recur))))
  (stop [component]
    component))

(defn new-task-submitter
  [new-tasks postfn api-url]
  (map->TaskSubmitter {:new-tasks new-tasks
                       :postfn postfn
                       :api-url api-url}))
