(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! chan alt! go-loop]]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]
            [diegoscheduler.http :as http]))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn- format-env [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(comment
  (format-env "a=b c=d e=f")
  (format-env nil)
  )

(defn- create-task [{callback-url :callback-url
                     api-url :api-url}
                    {:keys [args id guid dir domain docker-image env path result-file]}]
  (let [task {:domain domain
              :task_guid guid
              :log_guid guid
              :stack "lucid64"
              :privileged false
              :rootfs docker-image
              :action {:run {:path path
                             :args (clojure.string/split args #" ")}}
              :completion_callback_url callback-url
              :env (format-env env)
              :dir dir
              :result_file result-file
              :disk_mb 1000
              :memory_mb 1000}]
    (http/POST (str api-url "/tasks") task)))

(defn- remote-tasks [{api-url :api-url}]
  (let [[error, result] (http/GET (str api-url "/tasks"))]
    (if error
      (println error)
      result)))

(defrecord Diego [new-tasks processing-tasks stopper schedule api-url callback-url]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          new-tasks ([task _]
                     (create-task component task)
                     (recur))
          schedule ([_ _]
                    (>! processing-tasks {:processing (remote-tasks component)})
                    (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper
             :api-url api-url
             :callback-url callback-url)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego [new-tasks processing-tasks schedule api-url callback-url]
  (map->Diego {:new-tasks new-tasks
               :processing-tasks processing-tasks
               :schedule schedule
               :api-url api-url
               :callback-url callback-url}))
