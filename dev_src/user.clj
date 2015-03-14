(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh clear]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.server :as server]
            [diegoscheduler.diego :as diego]))

(def task-id (atom 1))

(def stop (fn []))
(defn start []
  (alter-var-root #'stop (constantly (http-kit/run-server server/app {:port 8080}))))
(defn reload [] (stop) (refresh :after 'user/start))
(defn failed? [task]
  (not= "" (:failure_reason task)))

(defn sorted-resolved []
  (map #(into (sorted-map) %) (:resolved @server/tasks)))

(comment
  (refresh)
  (clear)
  (reload)
  (diego/create-task {:id (swap! task-id inc)
                      :guid (str "foo" @task-id)
                      :domain "mydomainz"
                      :docker-image "docker:///camelpunch/env_writer"
                      :path "/usr/local/bin/env_writer.sh"
                      :args "foo /tmp/result"
                      :env "foo=bar"
                      :result-file "/tmp/result"})
    (diego/create-task {:id (swap! task-id inc)
                      :guid (str "foo" @task-id)
                      :domain "mydomainz"
                      :docker-image "docker:///camelpunch/s3copier"
                      :path "/bin/echo"
                      :args "foo"
                      :env "foo=bar"
                      :result-file "/tmp/result"})
  (count (diego/remote-tasks))
  (map keys (into (sorted-map) (diego/remote-tasks)))
  (count (sorted-resolved))
  (last (filter #(:failed %) (sorted-resolved)))
  (remove #(:failed %) (sorted-resolved))
  (first (:resolved @server/tasks))
  (reset! server/tasks {:resolved []
                        :pending []
                        :processing []})

  (count (:failed (let [resolved (:resolved @server/tasks)
                        {failed true successful false} (group-by failed? resolved)]
                    {:failed failed
                     :successful successful}))))
