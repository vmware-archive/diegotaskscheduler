(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! chan timeout alt! go-loop]]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]
            [diegoscheduler.http :as http]))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn format-env [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(comment
  (format-env "a=b c=d e=f")
  (format-env nil)
  )

(defprotocol Tasks
  (create-task [this message])
  (remote-tasks [this]))

(defrecord Diego [new-tasks processing-tasks stopper interval api-url callback-url]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          new-tasks ([task ch]
                     (create-task component task)
                     (recur))
          (timeout interval) ([_ _]
                              (>! processing-tasks {:processing (remote-tasks component)})
                              (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper
             :callback-url callback-url)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component)

  Tasks
  (create-task [this {:keys [args id guid dir domain docker-image env path result-file]}]
    (let [task {:domain domain
                :task_guid guid
                :log_guid guid
                :stack "lucid64"
                :privileged false
                :rootfs docker-image
                :action {:run {:path path
                               :args (clojure.string/split args #" ")}}
                :completion_callback_url (:callback-url this)
                :env (format-env env)
                :dir dir
                :result_file result-file
                :disk_mb 1000
                :memory_mb 1000}]
      (http/POST (str api-url "/tasks") task)))

  (remote-tasks [this]
    (let [[error, result] (http/GET (str api-url "/tasks"))]
      (if error
        (println error)
        result))))

(defn new-diego [new-tasks processing-tasks interval api-url callback-url]
  (map->Diego {:new-tasks new-tasks
               :processing-tasks processing-tasks
               :interval interval
               :api-url api-url
               :callback-url callback-url}))
