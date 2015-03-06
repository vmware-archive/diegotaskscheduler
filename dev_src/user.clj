(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh clear]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.server :as server]))

(def stop (fn []))
(defn start []
  (alter-var-root #'stop (constantly (http-kit/run-server server/app {:port 8080}))))
(defn reload [] (stop) (refresh :after 'user/start))

(comment
  (reload)
  )
