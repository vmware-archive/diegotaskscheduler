(ns diegoscheduler.charts-test
  (:require-macros [cemerick.cljs.test
                    :refer [is deftest run-tests testing]])
  (:require [cemerick.cljs.test :as t]
            [diegoscheduler.charts :as charts]))

(deftest data-manipulation
  (testing "raw data gets spread into a time series, in seconds, not millis"
    (is (= [{:time 10 :rate 0  :cell-quantity 1}
            {:time 11 :rate 0  :cell-quantity 2}
            {:time 12 :rate 20 :cell-quantity 3} ; take last received vals inside second
            {:time 13 :rate 20 :cell-quantity 3}
            {:time 14 :rate 5  :cell-quantity 4}
            {:time 15 :rate 5  :cell-quantity 4}
            {:time 16 :rate 5  :cell-quantity 4}]
           (charts/fill-gaps [{:time 10000 :rate 0  :cell-quantity 1}
                              {:time 11000 :rate 0  :cell-quantity 2}
                              {:time 12000 :rate 10 :cell-quantity 1}
                              {:time 12001 :rate 20 :cell-quantity 3}
                              {:time 14000 :rate 5  :cell-quantity 4}]
                             16)))))

(t/test-ns 'diegoscheduler.charts-test)
