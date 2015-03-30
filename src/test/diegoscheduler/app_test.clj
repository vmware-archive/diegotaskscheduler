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
                 app (new-app (chan) finished-tasks (chan) (chan))]
             (run app
               (fn [running-app]
                 (>!! finished-tasks {:some :task})
                 (<!! (timeout 100))
                 (get-in (deref (:state running-app))
                         [:tasks :resolved])))))))

  (testing "capacity fails get retried with a prepended guid"))
