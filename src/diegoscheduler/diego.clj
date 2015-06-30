(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! <! >! chan alt! go-loop onto-chan]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [clj-http.client :as client]
            [diegoscheduler.http :as http]))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn- format-env [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(defn create-task [{:keys [domain guid log_guid rootfs path
                           args env dir result-file]}]
  {:domain domain
   :task_guid guid
   :log_guid log_guid
   :stack "lucid64"
   :privileged true
   :rootfs rootfs
   :action {:run {:path path
                  :args (s/split args #" ")}}
   :env (format-env env)
   :dir dir
   :result_file result-file
   :disk_mb 1000
   :memory_mb 1000})

(defn- remote-tasks [{getfn :getfn
                      api-url :api-url}]
  (let [[error, result] (getfn (str api-url "/tasks"))]
    (if error
      (log/error error)
      result)))

(defn- delete-completed [{deletefn :deletefn api-url :api-url}
                         tasks]
  (let [completed-tasks (filter #(= "COMPLETED" (:state %)) tasks)]
    (doseq [t completed-tasks]
      (deletefn (str api-url "/tasks/" (:task_guid t))))))

(defrecord Diego [new-tasks tasks-from-diego schedule
                  getfn postfn deletefn
                  api-url
                  stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (let [tasks (remote-tasks component)]
                        (delete-completed component tasks)
                        (doseq [t tasks]
                          (log/info (:state t) (:task_guid t)))
                        (onto-chan tasks-from-diego tasks false)
                        (recur)))
          stopper :stopped))
      (go-loop []
        (when-let [task (<! new-tasks)]
          (if (:task_guid task)
            (do
              (log/info "POSTing to diego:" (:task_guid task))
              (postfn (str api-url "/tasks") task))
            (log/error "Got a bad task:" task))
          (recur)))

      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-diego [new-tasks tasks-from-diego schedule
                 getfn postfn deletefn
                 api-url]
  (map->Diego {:new-tasks new-tasks
               :tasks-from-diego tasks-from-diego
               :schedule schedule
               :getfn getfn
               :postfn postfn
               :deletefn deletefn
               :api-url api-url}))
