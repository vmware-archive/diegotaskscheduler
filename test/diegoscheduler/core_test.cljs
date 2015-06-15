(ns diegoscheduler.core-test
  (:require-macros [cemerick.cljs.test
                    :refer [is deftest run-tests testing]])
  (:require [cemerick.cljs.test :as t]
            [diegoscheduler.core :refer [handle-task-update]]))

(deftest handle-task-updates
  (testing "adds pendings"
    (let [state {:pending []
                 :successful []
                 :running []
                 :failed []}]
      (is (= {:pending [{:task_guid "asdf" :state "PENDING"}]
              :successful []
              :running []
              :failed []}
             (handle-task-update state {:task_guid "asdf" :state "PENDING"})))))
  (testing "moves to success"
    (let [state {:pending [{:task_guid "asdf" :state "PENDING"}]
                 :successful [{:task_guid "zxcv" :state "COMPLETED"}]
                 :running []
                 :failed []}]
      (is (= {:pending []
              :successful [{:task_guid "zxcv" :state "COMPLETED"}
                           {:task_guid "asdf" :state "COMPLETED"}]
              :running []
              :failed []}
             (handle-task-update state {:task_guid "asdf" :state "COMPLETED"})))))
  (testing "adds failures"
    (let [state {:pending []
                 :successful []
                 :running []
                 :failed []}]
      (is (= [{:task_guid "12345" :failed true}]
             (:failed (handle-task-update state {:task_guid "12345" :failed true}))))))
  (testing "doesn't duplicate failures"
    (let [state {:pending []
                 :successful []
                 :running []
                 :failed [{:task_guid "12345" :failed true}]}]
      (is (= state
             (handle-task-update state {:task_guid "12345" :failed true}))))))

(t/test-ns 'diegoscheduler.core-test)



