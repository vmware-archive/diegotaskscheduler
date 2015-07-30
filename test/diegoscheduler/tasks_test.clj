(ns diegoscheduler.tasks-test
  (:require [clojure.test :refer [deftest testing is]]
            [diegoscheduler.tasks :as tasks]))

(deftest moving-a-task
  (let [app-state {:states {:queued []
                            :running [{:task_guid "foo" :state "RUNNING"}]
                            :successful []
                            :failed []}
                   :do-not-run #{}
                   :cell-quantity 0
                   :rate 0}
        new-task-state {:task_guid "foo" :state "COMPLETED"}]
    (testing "prevents future running state, because the first state is running"
      (let [after-move (tasks/move-task app-state new-task-state)]
        (is (= [       []        [new-task-state] ]
               ((juxt  :running  :successful      )
                (:states after-move))))))
    (testing "removes old state"
      (is (empty?
           (get-in
            (tasks/move-task app-state new-task-state)
            [:states :running]))))
    (testing "adds new state"
      (is (= [new-task-state]
             (get-in
              (tasks/move-task app-state new-task-state)
              [:states :successful]))))))
