(ns test.diegoscheduler.diego-test
  (:require [clojure.core.async :refer [chan >!! <!!]]
            [com.stuartsierra.component :as component]
            [diegoscheduler.diego :refer :all]
            [clojure.test :refer :all]))

(defn- wrap [diego f]
  (try
    (component/start diego)
    (f)
    (finally
      (component/stop diego))))

(deftest task-creation
  (testing "valid task gets sent off"
    (is (= ["foo" "bar"]
           (let [new-tasks (chan)
                 results (chan)
                 postfn (fn [m] (>!! results m))
                 diego (new-diego new-tasks (chan) (chan) (fn [])
                                  postfn "http://bar.com")]
             (wrap diego
                   (fn []
                     (>!! new-tasks {:args "foo bar"
                                     :id "myid"
                                     :guid "myguid"
                                     :dir "/some/dir"
                                     :domain "mydomain"
                                     :docker-image "docker:///image"
                                     :env "FOO=BAR"
                                     :path "/path/to/exec"
                                     :result-file "/tmp/result"})
                     (-> (<!! results) :action :run :args))))))))
