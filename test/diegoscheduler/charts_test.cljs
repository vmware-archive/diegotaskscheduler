(ns diegoscheduler.charts-test
  (:require-macros [cemerick.cljs.test
                    :refer [is deftest run-tests testing]])
  (:require [cemerick.cljs.test :as t]
            [diegoscheduler.charts :as charts]))

(deftest data-manipulation
  (testing "raw data gets spread into a time series"
    (is (= [{:time 10 :rate 0}
            {:time 11 :rate 0}
            {:time 12 :rate 10}
            {:time 13 :rate 10}
            {:time 14 :rate 5}
            {:time 15 :rate 5}
            {:time 16 :rate 5}]
           (charts/fill-gaps [{:time 10 :rate 0}
                              {:time 12 :rate 10}
                              {:time 14 :rate 5}]
                             16)))))

(t/test-ns 'diegoscheduler.charts-test)
