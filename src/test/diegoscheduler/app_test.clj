(ns test.diegoscheduler.app-test
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan >!! <!! sliding-buffer timeout]]
            [diegoscheduler.app :refer :all]
            [clojure.test :refer :all]))

(defn- run [com f]
  (try
    (f (component/start com))
    (finally
      (component/stop com))))

(deftest finished-tasks
  (testing "regular fails get added to the resolved collection"
    (is (= [{:some :task}]
           (let [finished-tasks (chan)
                 app (new-app (chan) finished-tasks (chan) (chan) 999)]
             (run app
               (fn [running-app]
                 (>!! finished-tasks {:some :task})
                 (<!! (timeout 100))
                 (get-in (deref (:state running-app))
                         [:tasks :resolved])))))))

  (testing "capacity fails get put onto retry channel with a prepended guid"
    (let [capacity-fail-task (fn [guid] {:task_guid guid :failure_reason "insufficient resources"})
          finished-tasks (chan)
          retry-tasks (chan)
          app (new-app (chan) finished-tasks retry-tasks (chan) 0)
          finish-task (fn [task]
                        (>!! finished-tasks task)
                        (<!! retry-tasks))]
      (run app
        (fn [_]
          (is (= (capacity-fail-task "retry-original-guid")
                 (finish-task (capacity-fail-task "original-guid"))))
          (is (= (capacity-fail-task "retry-retry-original-guid")
                 (finish-task (capacity-fail-task "retry-original-guid"))))))))
)
