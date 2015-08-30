(ns diegoscheduler.task-poller-test
  (:require [diegoscheduler.task-poller :refer [new-task-poller]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!! onto-chan put! timeout alt!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-polling
  (testing "duplicates aren't sent if they were recently received (configurable)"
    (let [fire-now            (chan)
          output              (chan)
          getfn               (fn [uri tasks]
                                (put! tasks [{:task_guid "foo"
                                              :created_at 0} ; microseconds}
                                             {:task_guid "bar"
                                              :created_at 1000000} ; microseconds (1s)
                                             {:task_guid "baz"
                                              :created_at 2000000} ; microseconds (over 3s)
                                             ]))
          current-time        (atom 0)
          task-poller         (new-task-poller output
                                               (constantly fire-now)
                                               (fn [] @current-time)
                                               3000 ; milliseconds
                                               getfn
                                               "http://my.api")
          running-task-poller (start task-poller)]
      (reset! current-time 2000)
      (>!! fire-now :please)
      (is (= "foo" (:task_guid (<!! output))))
      (is (= "bar" (:task_guid (<!! output))))
      (is (= "baz" (:task_guid (<!! output))))
      (reset! current-time 2999)
      (>!! fire-now :please)
      (put! output "nothing available")
      (is (= "nothing available" (<!! output))) ; because (2999 - 3000) < all task timestamps, they all remain
      (reset! current-time 3001)
      (>!! fire-now :please)
      (is (= "foo" (:task_guid (<!! output)))) ; foo becomes available again, because 3001 - 3000 = 1, greater than 0
      (put! output "end")
      (is (= "end" (<!! output)))
      (stop running-task-poller))))
