(ns user
  (:require [reloaded.repl :refer [system init start stop go reset]]
            [diegoscheduler.systems :refer [dev-system]]
            [clojure.tools.namespace.repl :refer [refresh clear]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.server :as server]
            [diegoscheduler.diego :as diego]
            [overtone.at-at :as atat]))

(reloaded.repl/set-init! dev-system)

(def task-id (atom 1))

(defn sorted-resolved []
  (map #(into (sorted-map) %) (:resolved @server/tasks)))

(comment
  (refresh)
  (clear)

  (go)
  (:web system)
  (stop)
  (reset)

  (atat/stop-and-reset-pool! server/sched-pool)
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
                        :processing []})

  (count (:failed (let [resolved (:resolved @server/tasks)
                        {failed true successful false} (group-by :failed resolved)]
                    {:failed failed
                     :successful successful}))))
