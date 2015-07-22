(ns diegoscheduler.http-test
  (:require [diegoscheduler.http :refer :all]
            [clojure.test :refer :all]
            [clj-http.client :as http]))

(deftest POSTing
  (testing "Success"
    (is (= {"foo" "bar"}
           (let [[_ result] (POST "http://eu.httpbin.org/post" {:foo "bar"})]
             (-> result :data http/json-decode)))))
  (testing "Unknown Host"
    (is (= ["Unknown Host" nil] (POST "http://made.up.place.hopefully.will.never.exist/" {:foo "bar"}))))
  (testing "Connection Refused"
    (is (= ["Connection Refused", nil] (POST "http://127.0.0.1:9999" {:foo "bar"}))))
  (testing "400"
    (is (= ["400" nil] (POST "http://eu.httpbin.org/status/400" {:foo "bar"})))))

(deftest GETing
  (testing "Success"
    (is (= "http://eu.httpbin.org/get"
           (let [[_ result] (GET "http://eu.httpbin.org/get")]
             (result :url)))))
  (testing "Unknown Host"
    (is (= ["Unknown Host" nil] (GET "http://made.up.place.hopefully.will.never.exist/"))))
  (testing "Connection Refused"
    (is (= ["Connection Refused", nil] (GET "http://127.0.0.1:9999")))))

(deftest DELETEing
  (testing "Success"
    (is (= "http://eu.httpbin.org/delete"
           (let [[_ result] (DELETE "http://eu.httpbin.org/delete")]
             (result :url)))))
  (testing "Not Found"
    (is (= ["Not Found" nil] (DELETE "http://www.andrewbruce.net/a-non-existent-place")))))
