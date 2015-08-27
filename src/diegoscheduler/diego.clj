(ns diegoscheduler.diego
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! <! >! chan alt! go-loop onto-chan]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire]
            [diegoscheduler.http :as http]))

(defn parse-task
  [raw-task]
  (cheshire/parse-string raw-task true))

(defn- format-env
  [s]
  (map (fn [[_ name value]] {:name name :value value})
       (re-seq #"(\S+)=(\S+)" (or s ""))))

(defn create-task
  [{:keys [domain guid log_guid rootfs path
           args env dir result-file]}]
  {:domain domain
   :task_guid guid
   :log_guid log_guid
   :privileged true
   :stack "lucid64"
   :rootfs rootfs
   :action {:run {:user "root"
                  :path path
                  :args (s/split args #" ")}}
   :env (format-env env)
   :dir dir
   :result_file result-file
   :disk_mb 1000
   :memory_mb 1000})
