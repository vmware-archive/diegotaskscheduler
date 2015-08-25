(ns diegoscheduler.http-test
  (:require [diegoscheduler.http :refer :all]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan <!! go alt! timeout]]
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
  (testing "Success puts the JSON parsed body on a channel"
    (let [response (chan)
          timeout-ch (timeout 1000)]
      (GET "http://eu.httpbin.org/get" response)
      (go
        (is (= "Basic Og=="
               (:Authorization (alt!
                                 response  ([res _] (:headers res))
                                 timeout-ch "Timed out when waiting for result"))))))))

(deftest DELETEing
  (testing "Success puts the JSON parsed body on a channel"
    (let [response (chan)
          timeout-ch (timeout 1000)]
      (DELETE "http://eu.httpbin.org/delete" response)
      (go
        (is (= "http://eu.httpbin.org/delete"
               (alt!
                 response ([res _] (:url res))
                 timeout-ch "Timed out")))))))
