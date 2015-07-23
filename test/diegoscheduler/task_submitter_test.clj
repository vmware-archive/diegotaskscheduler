(ns diegoscheduler.task-submitter-test
  (:require [diegoscheduler.task-submitter :refer [new-task-submitter]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-submission
  (testing "post fn gets called with /tasks path and task"
    (let [new-tasks (chan)
          call (atom nil)
          postfn (fn [url task] (reset! call [url task]))
          task-submitter (new-task-submitter new-tasks
                                             postfn
                                             "http://my.api")
          running-task-submitter (start task-submitter)
          task {:task_guid "foo"}]
      (>!! new-tasks task)
      (is (= ["http://my.api/tasks" task] @call))
      (stop running-task-submitter))))
