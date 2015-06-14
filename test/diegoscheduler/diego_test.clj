(ns diegoscheduler.diego-test
  (:require [diegoscheduler.diego :refer [new-diego]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [go chan close! >! <! >!! <!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-receive
 (testing "Processing tasks are sent to the appropriate channel on a schedule"
   (let [new-tasks (chan)
         fire-now (chan)
         schedule (fn [] fire-now)
         output (chan)
         getfn (fn [] [nil, [{:some :task}]])
         diego (new-diego (chan) output schedule
                          getfn (fn []))
         running-diego (start diego)]
     (>!! fire-now :please)
     (let [item (<!! output)]
       (is (= {:tasks [{:some :task}]} item)))
     (stop diego))))
