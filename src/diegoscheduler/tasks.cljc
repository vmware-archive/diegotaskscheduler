(ns diegoscheduler.tasks)

(defn- same-guid-as
  [m]
  #(= (:task_guid m) (:task_guid %)))

(defn- remove-old-state
  [m task]
  (reduce (fn [acc [state tasks]]
            (merge acc
                   {state (vec (remove (same-guid-as task) tasks))}))
          {}
          m))

(defn- do-not-run
  [m task]
  (update-in m [:do-not-run] conj (:task_guid task)))

(defn state-of
  [task]
  (if (:failed task)
    :failed
    (case (:state task)
      "COMPLETED" :successful
      "RUNNING" :running
      "PENDING" :pending
      "QUEUED" :queued)))

(defn add-new-state
  [m task]
  (update-in m [(state-of task)] conj task))

(defn move-task
  [m task]
  (-> m
      (do-not-run task)
      (update-in [:states] remove-old-state task)
      (update-in [:states] add-new-state task)))

(defn now-running
  [m task]
  (if (some #{(:task_guid task)} (:do-not-run m))
    m
    (move-task m task)))
