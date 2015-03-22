(ns user
  (:require [reloaded.repl :refer [system init start stop go reset set-init!]]
            [clojure.core.async :refer [put! chan]]
            [diegoscheduler.systems :refer [main-system]]
            [clojure.tools.namespace.repl :refer [refresh clear set-refresh-dirs]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.diego :as diego]
            [environ.core :refer [env]]))

(set-init! #(main-system (:vcap-app-host env)
                         (:port env)
                         (:api-url env)))

(def task-id (atom 1))

(comment
  (refresh)
  (clear)

  (go)
  (stop)
  (reset)
  (:web system)
  (:channel (:updater system))

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
  (map keys (into (sorted-map) (diego/remote-tasks))))
