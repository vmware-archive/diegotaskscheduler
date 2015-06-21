(ns diegoscheduler.resubmitter
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop pipeline close! <!]])
  (:import java.util.UUID))

(def ^:private parallelism 1)
(def ^:private close-when-from-closes? false)

(defn- capacity-failure? [t]
  (= "insufficient resources" (:failure_reason t)))

(defn- assign-new-guid [tasks]
  (fn [failure]
    (let [new-guid (str (UUID/randomUUID))
          old-task (get @tasks (:task_guid failure))
          new-task (assoc old-task :task_guid new-guid)]
      (log/info "Assigned guid" (:task_guid new-task)
                "to failed guid" (:task_guid failure)
                "from matching old guid" (:task_guid old-task))
      (swap! tasks dissoc (:task_guid failure))
      new-task)))

(defn make-resubmittable [tasks]
  (comp
   (filter capacity-failure?)
   (map (assign-new-guid tasks))))

(defrecord Resubmitter [from-diego to-diego from-user]
  component/Lifecycle
  (start [component]
    (let [tasks (atom {})]
      (pipeline parallelism to-diego
                (make-resubmittable tasks) from-diego
                close-when-from-closes?
                (fn [e] (log/error "Problem:" e)))
      (go-loop []
        (when-let [new-task (<! from-user)]
          (swap! tasks assoc (:task_guid new-task) new-task)
          (recur)))
      (assoc component :tasks tasks)))
  (stop [component]
    (close! to-diego)
    component))

(defn new-resubmitter [from-diego to-diego from-user]
  (map->Resubmitter {:from-diego from-diego
                     :to-diego to-diego
                     :from-user from-user}))
