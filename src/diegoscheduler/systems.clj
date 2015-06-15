(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan timeout split pipeline]]
            [environ.core :refer [env]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]
            [diegoscheduler.http :as http])
  (:import java.util.UUID)
  (:gen-class))

(def ^:private update-interval 500)

(def ^:private assign-new-guids
  (map #(assoc % :task_guid (str (UUID/randomUUID)))))

(defn- capacity-failure? [t]
  (= "insufficient resources" (:failure_reason t)))

(defn main-system [port-str api-url ws-url]
  (let [port (Integer. port-str)
        new-tasks (chan)
        tasks-from-diego (chan)
        [retries tasks-for-display] (split capacity-failure? tasks-from-diego)
        client-pushes (chan)
        schedule (fn [] (timeout update-interval))
        tasks-url (str api-url "/tasks")
        getfn (fn [] (http/GET tasks-url))
        postfn (fn [task] (http/POST tasks-url task))]
    (pipeline 1 new-tasks assign-new-guids retries false
              (fn [e] (println "Problem:" e)))
    (component/system-map
     :diego (new-diego new-tasks tasks-from-diego schedule getfn postfn)
     :web (new-web-server new-tasks tasks-for-display port ws-url))))

(defn -main []
  (let [{:keys [port api-url ws-url]} env]
    (if (and port api-url ws-url)
      (component/start (main-system port api-url ws-url))
      (do
        (.println *err* "ERROR: must set PORT, API_URL and WS_URL.")
        (System/exit 1)))))
