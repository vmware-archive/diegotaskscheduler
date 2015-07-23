(ns diegoscheduler.task-poller-test
  (:require [diegoscheduler.task-poller :refer [new-task-poller]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-polling
  (testing "task state changes are sent to the appropriate channel on a schedule"
    (let [fire-now (chan)
          output (chan)
          getfn (fn [uri] 
                  (if (= uri "http://my.api/tasks")
                    [nil [{:task_guid "foo"} {:task_guid "bar"}]]
                    [nil [{:bad :uri :buddy :boy}]]))
          postfn (constantly nil)
          deletefn (constantly nil)
          task-poller (new-task-poller output
                                       (constantly fire-now)
                                       getfn deletefn
                                       "http://my.api")
          running-task-poller (start task-poller)]
      (>!! fire-now :please)
      (is (= {:task_guid "foo"} (<!! output)))
      (is (= {:task_guid "bar"} (<!! output)))
      (stop running-task-poller)))

  (testing "completed tasks get resolved"
    (let [fire-now (chan)
          input (chan)
          output (chan)
          getfn (fn [uri]
                  [nil [{:state "COMPLETED" :task_guid "foo"}
                        {:state "COMPLETED" :task_guid "bar"}
                        {:state "RUNNING" :task_guid "baz"}]])
          postfn (fn [])
          deleted-uris (atom [])
          deletefn (fn [uri] (swap! deleted-uris conj uri))
          task-poller (new-task-poller output
                                       (constantly fire-now)
                                       getfn deletefn
                                       "http://my.api")
          running-task-poller (start task-poller)]
      (>!! fire-now :please)
      (<!! output)
      (<!! output)
      (<!! output)
      (is (= @deleted-uris
             ["http://my.api/tasks/foo" "http://my.api/tasks/bar"]))
      (stop running-task-poller))))
