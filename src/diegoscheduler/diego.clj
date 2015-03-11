(ns diegoscheduler.diego
  (:require [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]))

(def api-url "http://192.168.11.11:8888/v1")
(def my-ip "10.61.6.96")
(def port 8080)
(def completion-callback-url (str "http://" my-ip ":" port "/taskfinished"))
(defn GET [path]
  (client/get (str api-url path) {:as :json}))
(defn POST [path body]
  (client/post (str api-url path) {:body body :as :json}))
(defn add-task [opts] (POST "/tasks" (client/json-encode opts)))

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

(defn remote-tasks [] (map clojure.walk/keywordize-keys (:body (GET "/tasks"))))

(defn create-task [{:keys [id guid domain docker-image path args] :as message}]
  (try+
   (add-task {:domain domain
              :task_guid guid
              :log_guid guid
              :stack "lucid64"
              :privileged false
              :rootfs docker-image
              :action {:run {:path path
                             :args (clojure.string/split args #" ")}}
              :completion_callback_url completion-callback-url
              :disk_mb 1000
              :memory_mb 1000})
   (catch [:status 400] {:keys [body]}
     body)))
