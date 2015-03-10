(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce new-task (atom {:id 1
                         :guid "task1"
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

(defn guid [t]
  (str "task" (:id t)))

(defn inc-id [m]
  (let [with-updated-id (update-in m [:id] inc)]
    (assoc with-updated-id :guid (guid with-updated-id))))

(defn upload-task []
  (put! upch @new-task)
  (swap! new-task inc-id))

(defn event-update [a attr]
  (fn [e]
    (swap! a #(assoc % attr (-> e .-target .-value)))))

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
                        :value (guid @new-task)}]]
    (input new-task :domain "Domain")
    (input new-task :docker-image "Docker image")
    (input new-task :path "Path to executable")
    (input new-task :args "Arguments (space sep)")
    [:button#add-task {:name (str "task" (:id @new-task))
                       :on-click upload-task} "Add " (guid @new-task)]]
   [:p (str @new-task)]
   [:div
    [:h2 "With Diego"]
    [:ul
]]
   [:div
    [:h2 "Successful Tasks"]]
   [:div
    [:h2 "Failed Tasks"]]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
