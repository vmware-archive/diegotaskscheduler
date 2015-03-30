(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan timeout]]
            [environ.core :refer [env]]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]
            [diegoscheduler.http :as http])
  (:gen-class))

(def ^:private update-interval 500)

(defn main-system [port-str api-url callback-url]
  (let [port (Integer. port-str)
        new-tasks (chan)
        processing-tasks (chan 1 (map :processing))
        finished-tasks (chan)
        client-pushes (chan)
        schedule (fn [] (timeout update-interval))
        tasks-url (str api-url "/tasks")
        getfn (fn [] (http/GET tasks-url))
        postfn (fn [task] (http/POST tasks-url task))]
    (component/system-map
     :diego (new-diego new-tasks processing-tasks schedule
                       getfn postfn
                       callback-url)
     :app (new-app processing-tasks finished-tasks retry-tasks client-pushes)
     :web (new-web-server new-tasks finished-tasks client-pushes port))))

(defn -main []
  (let [{:keys [port api-url callback-url]} env]
    (if (and port api-url callback-url)
      (component/start (main-system port api-url callback-url))
      (do
        (.println *err* "ERROR: must set PORT, API_URL and CALLBACK_URL.")
        (System/exit 1)))))
