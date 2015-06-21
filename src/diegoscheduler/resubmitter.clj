(ns diegoscheduler.resubmitter
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [pipeline close!]])
  (:import java.util.UUID))

(def ^:private parallelism 1)
(def ^:private close-when-from-closes? false)

(defn- capacity-failure? [t]
  (= "insufficient resources" (:failure_reason t)))

(defn- assign-new-guid [failure]
  (let [new-task (assoc failure :task_guid (str (UUID/randomUUID)))]
    (log/info "Assigned guid" (:task_guid new-task)
              "to failed guid" (:task_guid failure))
    new-task))

(def make-resubmittable
  (comp
   (filter capacity-failure?)
   (map assign-new-guid)))

(defrecord Resubmitter [tasks-from-diego new-tasks]
  component/Lifecycle
  (start [component]
    (pipeline parallelism
              new-tasks make-resubmittable tasks-from-diego
              close-when-from-closes?
              (fn [e] (log/error "Problem:" e)))
    component)
  (stop [component]
    (close! new-tasks)
    component))

(defn new-resubmitter [tasks-from-diego new-tasks]
  (map->Resubmitter {:tasks-from-diego tasks-from-diego
                     :new-tasks new-tasks}))
