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
    (testing "prevents future running state from laggy server, because
    it doesn't make sense to run a task twice"
      (let [after-move (tasks/move-task   app-state  new-task-state)
            after-run  (tasks/now-running after-move (merge new-task-state
                                                            {:state "RUNNING"}))]
        (is (= {:running    []
                :successful [new-task-state]}
               (select-keys (:states after-run) [:running :successful])))))
    (testing "removes old state"
      (is (empty?
           (-> (tasks/move-task app-state new-task-state)
               (get-in [:states :running])))))
    (testing "adds new state"
      (is (= [new-task-state]
             (-> (tasks/move-task app-state new-task-state)
                 (get-in [:states :successful])))))))
