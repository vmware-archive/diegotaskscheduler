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
                           args callback-url
                           env dir result-file]}]
  {:domain domain
   :task_guid guid
   :log_guid guid
   :stack "lucid64"
   :privileged false
   :rootfs rootfs
   :action {:run {:path path
                  :args (s/split args #" ")}}
   :completion_callback_url callback-url
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

(defrecord Diego [new-tasks processing-tasks schedule
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
                      (>! processing-tasks {:processing (remote-tasks component)})
                      (recur))
          stopper :stopped))
      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego [new-tasks processing-tasks schedule
                 getfn postfn]
  (map->Diego {:new-tasks new-tasks
               :processing-tasks processing-tasks
               :schedule schedule
               :getfn getfn
               :postfn postfn}))
