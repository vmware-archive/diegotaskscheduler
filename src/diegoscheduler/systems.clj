(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan timeout]]
            [environ.core :refer [env]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]
            [diegoscheduler.http :as http])
  (:gen-class))

(def ^:private update-interval 500)

(defn main-system [port-str api-url ws-url]
  (let [port (Integer. port-str)
        new-tasks (chan)
        tasks-from-diego (chan)
        client-pushes (chan)
        schedule (fn [] (timeout update-interval))
        tasks-url (str api-url "/tasks")
        getfn (fn [] (http/GET tasks-url))
        postfn (fn [task] (http/POST tasks-url task))]
    (component/system-map
     :diego (new-diego new-tasks tasks-from-diego schedule getfn postfn)
     :web (new-web-server new-tasks tasks-from-diego port ws-url))))

(defn -main []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (.println *err* "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
