(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! chan timeout alt! go-loop]]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]))

(def api-url "http://192.168.11.11:8888/v1")
(defn GET [path]
  (client/get (str api-url path) {:as :json}))
(defn POST [path body]
  (client/post (str api-url path) {:body body :as :json}))
(defn add-task [opts] (POST "/tasks" (client/json-encode opts)))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn remote-tasks [] (map clojure.walk/keywordize-keys (:body (GET "/tasks"))))

(defn format-env [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(comment
  (format-env "a=b c=d e=f")
  (format-env nil)
  )

(defn create-task [{:keys [args id guid dir domain docker-image env path result-file] :as message} completion-callback-url]
  (try+
   (let [task {:domain domain
               :task_guid guid
               :log_guid guid
               :stack "lucid64"
               :privileged false
               :rootfs docker-image
               :action {:run {:path path
                              :args (clojure.string/split args #" ")}}
               :completion_callback_url completion-callback-url
               :env (format-env env)
               :dir dir
               :result_file result-file
               :disk_mb 1000
               :memory_mb 1000}]
     (add-task task)
     task)
   (catch [:status 400] {:keys [body]}
     {})))

(defrecord Diego [channel stopper period callback-url]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          processing-tasks (chan)]
      (go-loop []
        (alt!
          (timeout period) (do
                             (>! processing-tasks {:processing (remote-tasks)})
                             (recur))
          stopper :stopped))
      (assoc component
             :stopper stopper
             :channel processing-tasks
             :callback-url callback-url)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego [period callback-url]
  (map->Diego {:period period
               :callback-url callback-url}))
