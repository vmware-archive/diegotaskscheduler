(ns diegoscheduler.charts-test
  (:require [clojure.test :refer :all]
            [diegoscheduler.charts :as charts]))

(deftest convert-all-ms-to-s
  (is (= [{:foo "bar" :time 10}
          {:baz "qux" :time 20}]
         (charts/convert-all-ms-to-s [{:foo "bar" :time 10000}
                                      {:baz "qux" :time 20000}]
                                     :time))))

(deftest fill-gaps
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

(deftest scroll-position
  (testing "in 1st screen, no scroll"
    (is (= 0 (charts/scroll-position 10 1000))))
  (testing "in 2nd screen, container width"
    (is (= 1000 (charts/scroll-position 1010 1000))))
  (testing "in 3rd screen, container width * 2"
    (is (= 2000 (charts/scroll-position 2001 1000)))))
