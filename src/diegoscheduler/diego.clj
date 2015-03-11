(ns diegoscheduler.diego
  (:require [clj-http.client :as client]
            [slingshot.slingshot :refer [try+]]))

(def api-url "http://192.168.11.11:8888/v1")
(def my-ip "192.168.1.8")
(def port 8080)
(def completion-callback-url (str "http://" my-ip ":" port "/taskfinished"))
(defn GET [path]
  (client/get (str api-url path) {:as :json}))
(defn POST [path body]
  (client/post (str api-url path) {:body body :as :json}))
(defn add-task [opts] (POST "/tasks" (client/json-encode opts)))

(defn remote-tasks [] (:body (GET "/tasks")))

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

(defn parse-task [raw-task]
  (clojure.walk/keywordize-keys (client/json-decode raw-task)))

;;   (:require [clojure.tools.namespace.repl :refer [refresh clear]]
;;             [clojure.pprint :refer [pprint]]
;;             [clojure.repl :refer (apropos dir doc find-doc pst source)]
;;             [clj-http.client :as client]
;;             [slingshot.slingshot :refer [try+]]
;;             [clojure.walk :refer [keywordize-keys]]
;;             [ring.adapter.jetty :refer [run-jetty]]))

;; (def api-url "http://192.168.11.11:8888/v1")
;; (def my-ip "192.168.1.3")
;; (def port 3000)
;; (def completion-callback-url (str "http://" my-ip ":" port "/"))

;; (def task (atom 1))
;; (def task-prefix "task")
;; (defn current-guid [] (str task-prefix @task))

;; ;; http client
;; (defn GET [path]
;;   (client/get (str api-url path) {:as :json}))
;; (defn POST [path body]
;;   (client/post (str api-url path) {:body body :as :json}))

;; ;; http server
;; (def server (atom nil))
;; (def received-tasks (atom []))
;; (defn parse-callback [req]
;;   (keywordize-keys (client/json-decode (slurp (:body req)))))
;; (defn handler [request]
;;   (swap! received-tasks #(conj % (parse-callback request)))
;;   {:status 200
;;    :body "Thanks Diego!\n"})

;; (defn start-server []
;;   (reset! server (run-jetty handler {:port port :join? false})))

;; (defn remote-tasks [] (:body (GET "/tasks")))
;; (defn add-task [opts] (POST "/tasks" (client/json-encode opts)))
;; (defn cells [] (:body (GET "/cells")))

;; (defn failed? [task] (not= "" (:failure_reason task)))
;; (defn current? [task] (= (:task_guid task) (current-guid)))

;; (defn show-current [] (first (filter current? (remote-tasks))))
;; (defn next-guid! [] (str task-prefix (swap! task inc)))

;; (defn add-new []
;;   (let [guid (next-guid!)]
;;     (try+
;;      (add-task {:domain "foo"
;;                 :task_guid guid
;;                 :log_guid guid
;;                 :stack "lucid64"
;;                 :privileged false
;;                 :rootfs "docker:///camelpunch/simplesaver"
;;                 :action {:run {:path "/usr/bin/saver"
;;                                :args ["/tmp/storage" "foobar"]}}
;;                 :completion_callback_url completion-callback-url
;;                 :result_file "/tmp/storage"
;;                 :disk_mb 1000
;;                 :memory_mb 1000})
;;      (catch [:status 400] {:keys [body]}
;;        body))))

;; (defn short-status [job]
;;   (select-keys job [:cell_id :state :failure_reason :result :task_guid]))
