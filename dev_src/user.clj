(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh clear]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.server :as server]
            [diegoscheduler.diego :as diego]))

(def stop (fn []))
(defn start []
  (alter-var-root #'stop (constantly (http-kit/run-server server/app {:port 8080}))))
(defn reload [] (stop) (refresh :after 'user/start))
(defn failed? [task]
  (not= "" (:failure_reason task)))

(comment
  (refresh)
  (clear)
  (reload)
  (count (diego/remote-tasks))
  (count (:resolved @server/tasks))
  (first (:resolved @server/tasks))

  (count (:failed (let [resolved (:resolved @server/tasks)
                        {failed true successful false} (group-by failed? resolved)]
                    {:failed failed
                     :successful successful})))

  )
