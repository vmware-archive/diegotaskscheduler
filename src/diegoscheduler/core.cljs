(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce task (atom {:id 1
                     :domain "foo"
                     :docker-image "docker://camelpunch/s3copier"
                     :path "/usr/local/bundle/bin/bundle"
                     :args "exec ./copy.rb mysource mydest"}))
(def upch (chan))

(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/ws"))]
    (if error
      (js/console.log "Error:" (pr-str error))
      (do
        (go-loop []
          (when-let [msg (<! upch)]
            (>! ws-channel msg)
            (recur)))
        (go-loop []
          (when-let [message (<! ws-channel)]
            (js/console.log (str "Got this from server: " message))
            (recur)))))))

(defn inc-id [m]
  (update-in m [:id] inc))

(defn guid [t]
  (str "task" (:id t)))

(defn upload-task []
  (put! upch @task)
  (swap! task inc-id))

(defn event-update [a attr]
  (fn [e]
    (swap! a #(assoc % attr (-> e .-target .-value)))))

;; (add-task {:domain "foo"
;;            :task_guid guid
;;            :log_guid guid
;;            :stack "lucid64"
;;            :privileged false
;;            :rootfs "docker:///camelpunch/simplesaver"
;;            :action {:run {:path "/usr/bin/saver"
;;                           :args ["/tmp/storage" "foobar"]}}
;;            :completion_callback_url completion-callback-url
;;            :result_file "/tmp/storage"
;;            :disk_mb 1000
;;            :memory_mb 1000})

(defn input [a key label]
  (let [id (str "task-" key)]
    [:p
     [:label {:for id} label]
     [:input {:id id
              :size 30
              :value (key (deref a))
              :on-change (event-update a key)}]]))

(defn page []
  [:div
   [:h1 "Task Scheduler"]
   [:div
    [:h2 "Controls"]
    [:p
     [:label {:for "task_guid"} "GUID"]
     [:input#task-guid {:name "task_guid"
                        :disabled "disabled"
                        :size 9
                        :value (guid @task)}]]
    (input task :domain "Domain")
    (input task :docker-image "Docker image")
    (input task :path "Path to executable")
    (input task :args "Arguments (space sep)")
    [:button#add-task {:name (str "task" (:id @task))
                       :on-click upload-task} "Add " (guid @task)]]
   [:p (str @task)]
   [:div
    [:h2 "Successful Tasks"]]
   [:div
    [:h2 "Failed Tasks"]]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
