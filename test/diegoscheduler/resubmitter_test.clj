(ns diegoscheduler.resubmitter-test
  (:require [diegoscheduler.resubmitter :refer [new-resubmitter]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [go chan >!! <!! <!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest resubmitting
  (testing "it resubmits failures it has previously recorded"
    (let [from-diego          (chan)
          to-diego            (chan)
          from-user           (chan)
          resubmitter         (new-resubmitter from-diego to-diego from-user)
          running-resubmitter (start resubmitter)
          retrieved           (promise)]
      (>!! from-user {:task_guid "foo" :domain "mydomain"})
      (>!! from-diego {:task_guid "foo" :domain "diego-might-get-this-wrong"})
      (go (deliver retrieved (<! to-diego)))
      (is (= "mydomain" (:domain (deref retrieved 1000 "Timed out"))))))
  (testing "it drops failures it doesn't know about on the floor"
    (let [from-diego          (chan)
          to-diego            (chan)
          from-user           (chan)
          resubmitter         (new-resubmitter from-diego to-diego from-user)
          running-resubmitter (start resubmitter)
          retrieved           (promise)]
      (>!! from-user {:task_guid "foo" :domain "mydomain"})
      (>!! from-diego {:task_guid "bar" :domain ""})
      (go (deliver retrieved (<! to-diego)))
      (is (= "no delivery" (deref retrieved 1000 "no delivery"))))))
