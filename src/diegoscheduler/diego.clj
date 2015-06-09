(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! chan alt! go-loop]]
            [clojure.string :as s]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]
            [diegoscheduler.http :as http]))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn- format-env [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(defn create-task [{:keys [domain guid rootfs path
                           args env dir result-file]}]
  {:domain domain
   :task_guid guid
   :log_guid guid
   :stack "lucid64"
   :privileged false
   :rootfs rootfs
   :action {:run {:path path
                  :args (s/split args #" ")}}
   :env (format-env env)
   :dir dir
   :result_file result-file
   :disk_mb 1000
   :memory_mb 1000})

(defn- remote-tasks [{getfn :getfn}]
  (let [[error, result] (getfn)]
    (if error
      (println error)
      result)))

(defrecord Diego [new-tasks tasks-from-diego schedule
                  getfn postfn
                  stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          new-tasks ([task _]
                     (postfn task)
                     (recur))
          (schedule) ([_ _]
                      (let [tasks (remote-tasks component)]
                        (>! tasks-from-diego {:tasks tasks})
                        (recur)))
          stopper :stopped))
      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego [new-tasks tasks-from-diego schedule
                 getfn postfn]
  (map->Diego {:new-tasks new-tasks
               :tasks-from-diego tasks-from-diego
               :schedule schedule
               :getfn getfn
               :postfn postfn}))
