(ns diegoscheduler.http-test
  (:require [diegoscheduler.http :refer :all]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan <!! >! <! go go-loop alt! timeout]])
  (:use org.httpkit.fake))

(deftest POSTing
  (testing "JSON parsed body put onto the channel"
    (let [response (chan)
          message (promise)]
      (go
        (when-let [r (<! response)]
          (deliver message r)))
      (POST "http://eu.httpbin.org/post" "foo" response)
      (is (= "\"foo\"" (:data (deref message 1000 "Timed out")))))))

(deftest GETing
  (testing "Success puts the JSON parsed body on a channel"
    (let [response (chan)
          message (promise)]
      (go
        (when-let [r (<! response)]
          (deliver message (:foo r))))
      (with-fake-http ["http://my/place" "{\"foo\": \"bar\"}"]
        @(GET "http://my/place" response))
      (is (= "bar" (deref message 1000 "Timed out"))))))

(deftest DELETEing
  (testing "Success puts the JSON parsed body on a channel"
    (let [response (chan)
          message (promise)]
      (go
        (when-let [res (<! response)]
          (deliver message res)))
      (with-fake-http ["http://yo.dawg" {:status 200 :body "{\"hi\": \"there\"}"}]
        @(DELETE "http://yo.dawg" response))
      (is (= {:hi "there"} (deref message 1000 "Timed out")))))
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
