(ns diegoscheduler.rate-emitter-test
  (:require [diegoscheduler.rate-emitter :refer [new-rate-emitter]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest rate-emitting
  (testing "multiple ticks get averaged on a window"
    (let [completed-tasks (chan)
          tick (chan)
          rate (chan)
          window 2
          emitter (new-rate-emitter completed-tasks
                                    rate
                                    (constantly tick)
                                    window)
          running-emitter (start emitter)]
      (>!! completed-tasks {})
      (>!! completed-tasks {})
      (>!! completed-tasks {})
      (>!! completed-tasks {})
      (>!! completed-tasks {})
      (>!! tick :now)
      (is (= 2.5 (<!! rate)))
      (>!! completed-tasks {})
      (>!! tick :now)
      (is (= 3.0 (<!! rate)))
      (>!! completed-tasks {})
      (>!! completed-tasks {})
      (>!! tick :now)
      (is (= 1.5 (<!! rate)))
      (stop running-emitter))))
