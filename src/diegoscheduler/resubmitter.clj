(ns diegoscheduler.resubmitter
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop pipeline close! <!]])
  (:import java.util.UUID))

(def ^:private parallelism 1)
(def ^:private close-when-from-closes? false)

(defn- retriever [tasks]
  (fn [failure]
    (get @tasks (:task_guid failure))))

(defn- assign-new-guid [tasks]
  (fn [failure]
    (let [new-guid (str (UUID/randomUUID))
          old-task ((retriever tasks) failure)
          new-task (assoc old-task :task_guid new-guid)]
      (log/info "Assigned guid" (:task_guid new-task)
                "to failed guid" (:task_guid failure)
                "from matching old guid" (:task_guid old-task))
      (swap! tasks dissoc (:task_guid failure))
      new-task)))

(defn make-resubmittable [tasks]
  (comp
   (filter (retriever tasks))
   (map (assign-new-guid tasks))))

(defrecord Resubmitter [from-diego to-diego from-user]
  component/Lifecycle
  (start [component]
    (let [user-submitted-tasks (atom {})]
      (pipeline parallelism to-diego
                (make-resubmittable user-submitted-tasks) from-diego
                close-when-from-closes?
                (fn [e] (log/error "Problem:" e)))
      (go-loop []
        (when-let [new-task (<! from-user)]
          (log/info "Resubmission db size:" (count (swap! user-submitted-tasks assoc (:task_guid new-task) new-task)))
          (recur)))
      component))
  (stop [component]
    (close! to-diego)
    component))

(defn new-resubmitter [from-diego to-diego from-user]
  (map->Resubmitter {:from-diego from-diego
                     :to-diego to-diego
                     :from-user from-user}))
