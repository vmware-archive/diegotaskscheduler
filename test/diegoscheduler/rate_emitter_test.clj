(ns diegoscheduler.rate-emitter-test
  (:require [diegoscheduler.rate-emitter :refer [new-rate-emitter rate]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest calculate-rate
  (testing "given a data structure, calculate rate of last n completions, without dupes"
    (is (= 5/4
           (rate 4 [[{:task_guid "z"} {:task_guid "x"}]
                    [{:task_guid "a"} {:task_guid "b"}]
                    [{:task_guid "c"}]
                    [{:task_guid "d"} {:task_guid "d"}]
                    [{:task_guid "e"}]])))))

(deftest rate-emitting
  (testing "multiple ticks get averaged on a window, ignoring duplicates"
    (let [completed-tasks (chan)
          tick (chan)
          rate (chan)
          window 2
          emitter (new-rate-emitter completed-tasks
                                    rate
                                    (constantly tick)
                                    window)
          running-emitter (start emitter)]
      (>!! completed-tasks {:task_guid "a"})
      (>!! completed-tasks {:task_guid "b"})
      (>!! completed-tasks {:task_guid "c"})
      (>!! completed-tasks {:task_guid "d"})
      (>!! completed-tasks {:task_guid "e"})
      (>!! tick :now)
      (is (= 2.5 (<!! rate)))
      (>!! completed-tasks {:task_guid "f"})
      (>!! completed-tasks {:task_guid "f"})
      (>!! completed-tasks {:task_guid "f"})
      (>!! tick :now)
      (is (= 3.0 (<!! rate)))
      (>!! completed-tasks {:task_guid "g"})
      (>!! completed-tasks {:task_guid "h"})
      (>!! tick :now)
      (is (= 1.5 (<!! rate)))
      (stop running-emitter))))
