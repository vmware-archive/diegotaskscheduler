(ns diegoscheduler.http-test
  (:require [diegoscheduler.http :refer :all]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan <!! >! go go-loop alt! timeout]]
            [clj-http.client :as http])
  (:use org.httpkit.fake))

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
          timeout-ch (timeout 1000)
          message (chan)]
      (go
        (alt!
          response  ([res _] (>! message (:foo res)))
          timeout-ch ([_ _] (>! message "Timed out"))))
      (with-fake-http ["http://my/place" "{\"foo\": \"bar\"}"]
        @(GET "http://my/place" response))
      (is (= "bar" (<!! message))))))

(deftest DELETEing
  (testing "Success puts the JSON parsed body on a channel"
    (let [response (chan)
          timeout-ch (timeout 1000)
          message (chan)]
      (go
        (alt!
          response ([res _] (>! message res))
          timeout-ch ([_ _] (>! message "Timed out"))))
      (with-fake-http ["http://yo.dawg" {:status 200 :body "{\"hi\": \"there\"}"}]
        @(DELETE "http://yo.dawg" response))
      (is (= {:hi "there"} (<!! message)))))
  (testing "Success with empty body doesn't put to the channel"
    (let [response (chan)
          timeout-ch (timeout 1)
          message (chan)]
      (go
        (alt!
          response ([res _] (>! message "unexpected receipt"))
          timeout-ch ([_ _] (>! message "timeout"))))
      (with-fake-http ["http://some/place" {:status 200 :body ""}]
        @(DELETE "http://some/place" response))
      (is (= "timeout" (<!! message))))))
