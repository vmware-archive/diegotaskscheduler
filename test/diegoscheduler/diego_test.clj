(ns diegoscheduler.diego-test
  (:require [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :as component]
            [diegoscheduler.diego :refer :all]
            [clojure.test :refer :all]))

(defn- run [com f]
  (try
    (f (component/start com))
    (finally
      (component/stop com))))

(deftest task-create
  (is (= {:domain "mydomain"
          :task_guid "myguid"
          :log_guid "myguid"
          :stack "lucid64"
          :privileged false
          :rootfs "docker:///some/image"
          :action {:run {:path "mypath"
                         :args ["arg1" "arg2"]}}
          :completion_callback_url "http://call.me/plz"
          :env '({:name "a" :value "b"} {:name "c" :value "d"})
          :dir "/my/dir"
          :result_file "/my/result"
          :disk_mb 1000
          :memory_mb 1000}
         (create-task {:domain "mydomain"
                       :guid "myguid"
                       :rootfs "docker:///some/image"
                       :path "mypath"
                       :args "arg1 arg2"
                       :callback-url "http://call.me/plz"
                       :env "a=b c=d"
                       :dir "/my/dir"
                       :result-file "/my/result"}))))

(deftest task-post
  (testing "valid task gets sent off"
    (let [new-tasks (chan)
          post-args (chan)
          postfn (fn [m] (>!! post-args m))
          diego (new-diego new-tasks (chan) (fn [] (chan))
                           (fn []) postfn)
          input-task (create-task {:args "foo bar"
                                   :id "myid"
                                   :guid "myguid"
                                   :dir "/some/dir"
                                   :domain "mydomain"
                                   :rootfs "docker:///image"
                                   :env "FOO=BAR"
                                   :path "/path/to/exec"
                                   :result-file "/tmp/result"
                                   :callback-url "http://bar.com"})]
      (is (= {:disk_mb 1000,
              :memory_mb 1000,
              :privileged false,
              :dir "/some/dir",
              :completion_callback_url "http://bar.com",
              :log_guid "myguid",
              :env '({:name "FOO", :value "BAR"}),
              :stack "lucid64",
              :task_guid "myguid",
              :result_file "/tmp/result",
              :action {:run {:path "/path/to/exec", :args ["foo" "bar"]}},
              :domain "mydomain",
              :rootfs "docker:///image"}
             (run diego
               (fn [_]
                 (>!! new-tasks input-task)
                 (<!! post-args))
               ))))))

(deftest task-receive
  (testing "Processing tasks are sent to the appropriate channel on a schedule"
    (let [new-tasks (chan)
          fire-now (chan)
          schedule (fn [] fire-now)
          processing-tasks (chan)
          getfn (fn [] [nil, [{:some :task}]])
          diego (new-diego (chan) processing-tasks schedule
                           getfn (fn []))
          running-diego (component/start diego)]
      (>!! fire-now :please)
      (let [item (<!! processing-tasks)]
        (is (= {:processing [{:some :task}]} item)))
      (component/stop diego))))
