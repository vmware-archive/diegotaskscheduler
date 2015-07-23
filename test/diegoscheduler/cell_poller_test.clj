(ns diegoscheduler.cell-poller-test
  (:require [diegoscheduler.cell-poller :refer [new-cell-poller]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan <!! >!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest cell-polling
  (testing "cell numbers are sent to the provided channel on a schedule"
    (let [fire-now (chan)
          output (chan)
          cells (atom [{:cell_id "cell-0"}
                       {:cell_id "cell-1"}])
          getfn (fn [uri] 
                  (if (= uri "http://my.api/cells")
                    [nil @cells]
                    [nil [{:bad :uri :buddy :boy}]]))
          cell-poller (new-cell-poller output
                                       (constantly fire-now)
                                       getfn
                                       "http://my.api")
          running-cell-poller (start cell-poller)]
      (>!! fire-now :please)
      (is (= 2 (<!! output)))
      (>!! fire-now :please)
      (is (= 2 (<!! output)))
      (swap! cells conj {:cell_id "cell-2"})
      (>!! fire-now :please)
      (is (= 3 (<!! output)))
      (stop running-cell-poller))))
