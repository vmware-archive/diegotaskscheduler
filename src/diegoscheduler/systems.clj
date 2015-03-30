(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan timeout]]
            [environ.core :refer [env]]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]])
  (:gen-class))

(def ^:private update-interval 500)

(defn main-system [port-str api-url callback-url]
  (let [port (Integer. port-str)
        new-tasks (chan)
        processing-tasks (chan)
        schedule (timeout update-interval)]
    (component/system-map
     :diego (new-diego new-tasks processing-tasks schedule
                       api-url callback-url)
     :app (new-app new-tasks processing-tasks)
     :web (component/using
           (new-web-server port)
           [:app]))))

(defn -main []
  (let [{:keys [port api-url callback-url]} env]
    (if (and port api-url callback-url)
      (component/start (main-system port api-url callback-url))
      (do
        (.println *err* "ERROR: must set PORT, API_URL and CALLBACK_URL.")
        (System/exit 1)))))
