(ns test.diegoscheduler.diego-test
  (:require [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :as component]
            [diegoscheduler.diego :refer :all]
            [clojure.test :refer :all]))

(defn- run [com f]
  (try
    (f (component/start com))
    (finally
      (component/stop com))))

(deftest task-creation
  (testing "valid task gets sent off"
    (let [new-tasks (chan)
          post-args (chan)
          postfn (fn [m] (>!! post-args m))
          diego (new-diego new-tasks (chan) (fn [] (chan))
                           (fn []) postfn
                           "http://bar.com")]
      (is (= ["foo" "bar"]
             (run diego
               (fn [_]
                 (>!! new-tasks {:args "foo bar"
                                 :id "myid"
                                 :guid "myguid"
                                 :dir "/some/dir"
                                 :domain "mydomain"
                                 :docker-image "docker:///image"
                                 :env "FOO=BAR"
                                 :path "/path/to/exec"
                                 :result-file "/tmp/result"})
                 (-> (<!! post-args) :action :run :args))
               ))))))

(deftest task-updates
  (testing "Processing tasks are sent to the appropriate channel on a schedule"
    (let [new-tasks (chan)
          fire-now (chan)
          schedule (fn [] fire-now)
          processing-tasks (chan)
          getfn (fn [] [nil, [{:some :task}]])
          diego (new-diego (chan) processing-tasks schedule
                           getfn (fn [])
                           "http://some.url")
          running-diego (component/start diego)]
      (>!! fire-now :please)
      (let [item (<!! processing-tasks)]
        (is (= {:processing [{:some :task}]} item)))
      (component/stop diego))))
