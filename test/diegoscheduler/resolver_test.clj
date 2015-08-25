(ns diegoscheduler.resolver-test
  (:require [diegoscheduler.resolver :refer [new-resolver]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!! onto-chan put!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest resolver
  (testing "completed tasks get resolved"
    (let [deleted-uris (atom [])
          deletefn (fn [uri] (swap! deleted-uris conj uri))
          tasks-to-resolve (chan)
          resolver (new-resolver tasks-to-resolve
                                 deletefn
                                 "http://my.api")
          running-resolver (start resolver)]
      (>!! tasks-to-resolve {:task_guid "foo"})
      (>!! tasks-to-resolve {:task_guid "bar"})
      (is (= @deleted-uris
             ["http://my.api/tasks/foo" "http://my.api/tasks/bar"]))
      (stop running-resolver))))
