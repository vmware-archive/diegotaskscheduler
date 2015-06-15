(ns diegoscheduler.diego-test
  (:require [diegoscheduler.diego :refer [new-diego]]
            [clojure.test :refer :all]
            [clojure.core.async :refer [go chan close! >! <! >!! <!!]]
            [com.stuartsierra.component :refer [start stop]]))

(deftest task-receive
 (testing "task state changes are sent to the appropriate channel on a schedule"
   (let [new-tasks (chan)
         fire-now (chan)
         input (chan)
         output (chan)
         getfn (fn [uri] 
                 (if (= uri "http://my.api/tasks")
                   [nil [{:task_guid "foo"} {:task_guid "bar"}]]
                   [nil [{:bad :uri :buddy :boy}]]))
         postfn (constantly nil)
         deletefn (constantly nil)
         diego (new-diego input output
                          (constantly fire-now)
                          getfn postfn deletefn
                          "http://my.api")
         running-diego (start diego)]
     (>!! fire-now :please)
     (is (= {:task_guid "foo"} (<!! output)))
     (is (= {:task_guid "bar"} (<!! output)))
     (stop diego)))

 (testing "completed tasks get resolved"
   (let [new-tasks (chan)
         fire-now (chan)
         input (chan)
         output (chan)
         getfn (fn [uri]
                 [nil [{:state "COMPLETED" :task_guid "foo"}
                       {:state "COMPLETED" :task_guid "bar"}
                       {:state "RUNNING" :task_guid "baz"}]])
         postfn (fn [])
         deleted-uris (atom [])
         deletefn (fn [uri] (swap! deleted-uris conj uri))
         diego (new-diego input output
                          (constantly fire-now)
                          getfn postfn deletefn
                          "http://my.api")
         running-diego (start diego)]
     (>!! fire-now :please)
     (<!! output)
     (<!! output)
     (<!! output)
     (is (= @deleted-uris
            ["http://my.api/tasks/foo" "http://my.api/tasks/bar"]))
     (stop diego))))
