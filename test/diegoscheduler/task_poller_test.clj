(ns diegoscheduler.task-poller-test
  (:require [diegoscheduler.task-poller :refer [new-task-poller]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!! onto-chan put!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-polling
  (testing "task state changes are sent to the appropriate channel on a schedule"
    (let [fire-now (chan)
          output (chan)
          getfn (fn [uri tasks]
                  (if (= uri "http://my.api/tasks")
                    (put! tasks [{:task_guid "foo"} {:task_guid "bar"}])
                    (put! tasks [{:bad :uri :buddy :boy}])))
          deletefn (constantly nil)
          task-poller (new-task-poller output (constantly fire-now) getfn "http://my.api")
          running-task-poller (start task-poller)]
      (>!! fire-now :please)
      (is (= {:task_guid "foo"} (<!! output)))
      (is (= {:task_guid "bar"} (<!! output)))
      (is (put! output :still-open))
      (stop running-task-poller))))
