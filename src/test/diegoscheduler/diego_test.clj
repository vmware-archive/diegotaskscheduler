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
    (is (= ["foo" "bar"]
           (let [new-tasks (chan)
                 post-args (chan)
                 postfn (fn [m] (>!! post-args m))
                 diego (new-diego new-tasks (chan) (chan) (fn [])
                                  postfn "http://bar.com")]
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
                     (-> (<!! post-args) :action :run :args))))))))
