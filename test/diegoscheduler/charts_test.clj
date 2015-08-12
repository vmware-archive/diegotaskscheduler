(ns diegoscheduler.charts-test
  (:require [clojure.test :refer :all]
            [diegoscheduler.charts :as charts]))

(deftest convert-all-ms-to-s
  (is (= [{:foo "bar" :time 10}
          {:baz "qux" :time 20}]
         (charts/convert-all-ms-to-s [{:foo "bar" :time 10000}
                                      {:baz "qux" :time 20000}]
                                     :time))))

(deftest data-manipulation
  (testing "chart data gets spread into a time series"
    (is (= [{:time 10 :rate 0  :cell-quantity 1}
            {:time 11 :rate 0  :cell-quantity 2}
            {:time 12 :rate 20 :cell-quantity 3} ; take last received vals inside second
            {:time 13 :rate 20 :cell-quantity 3}
            {:time 14 :rate 5  :cell-quantity 4}
            {:time 15 :rate 5  :cell-quantity 4}
            {:time 16 :rate 5  :cell-quantity 4}]
           (charts/fill-gaps [{:time 10 :rate 0  :cell-quantity 1}
                              {:time 11 :rate 0  :cell-quantity 2}
                              {:time 12 :rate 10 :cell-quantity 1}
                              {:time 12 :rate 20 :cell-quantity 3}
                              {:time 14 :rate 5  :cell-quantity 4}]
                             :time 16)))))

(deftest width
  (testing "it returns the width of the chart, based on the time data and x interval"
    (is (= 2500 (charts/width [{:time 10000} {:time 510000}] 5)))))
