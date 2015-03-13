(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def new-task (atom {:id 1
                     :guid "task1"
                     :domain (str "domain-" (js/Math.random))
                     :docker-image "docker:///camelpunch/s3copier"
                     :path "/usr/local/bundle/bin/bundle"
                     :args "exec ./copy.rb mysource mydest"}))
(defonce tasks (atom {:processing []
                      :successful []
                      :failed []}))
(def upch (chan))

(def num-visible-tasks 7)

(defn failed? [task]
  (not= "" (:failure_reason task)))

(defn update-tasks [m new-tasks]
  (let [resolved (:resolved new-tasks)
        {failed true successful false} (group-by failed? resolved)]
    (assoc m
      :failed failed
      :successful successful
      :processing (:processing new-tasks))))

(defn handle-tasks [new-tasks]
  (swap! tasks update-tasks new-tasks))

(def handlers
  {:tasks handle-tasks})

(defn no-handler [message]
  (js/console.log (str "No handler defined for message: " message)))

(defn route-message [message]
  (let [key (-> message keys first)]
    (if (contains? handlers key)
      ((key handlers) (key message))
      (no-handler message))))

(defn handle-outgoing [ws-channel]
  (go-loop []
    (when-let [msg (<! upch)]
      (>! ws-channel msg)
      (recur))))

(defn handle-incoming [ws-channel]
  (go-loop []
    (when-let [{message :message
                error :error} (<! ws-channel)]
      (if error
        (js/console.log (str "ERROR: " error))
        (route-message message))
      (recur))))

(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/ws"))]
    (if error
      (js/console.log "Error:" (pr-str error))
      (do
        (handle-outgoing ws-channel)
        (handle-incoming ws-channel)))))

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

(defn table [keyfn coll fields]
  [:table
     [:thead
      [:tr
       (for [heading (vals fields)]
         [:th {:key (str keyfn heading)} heading])]]
     [:tbody
      (for [t (take num-visible-tasks
                    (sort-by :created_at > (keyfn coll)))]
        [:tr {:key (str keyfn t)}
         (for [k (keys fields)]
           (if (= :created_at k)
             [:td {:key (str keyfn k)} (.toTimeString (js/Date. (/ (:created_at t) 1000000)))]
             [:td {:key (str keyfn k)} (k t)]))])]])

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
    (input new-task :args "Arguments")
    [:button#add-task {:name (str "task" (:id @new-task))
                       :on-click upload-task} "Add " (guid @new-task)]]
   [:p (str @new-task)]
   [:div
    [:h2 "Processing Tasks"]
    (table :processing @tasks {:task_guid "GUID"
                               :domain "Domain"
                               :state "State"
                               :cell_id "Cell"
                               :rootfs "Docker image"
                               :created_at "Time"})]
   [:div
    [:h2 "Successful Tasks"]
    (table :successful @tasks {:task_guid "GUID"
                               :state "State"
                               :cell_id "Cell"
                               :rootfs "Docker image"
                               :created_at "Time"})]
   [:div
    [:h2 "Failed Tasks"]
    (table :failed @tasks {:task_guid "GUID"
                           :domain "Domain"
                           :state "State"
                           :cell_id "Cell"
                           :rootfs "Docker image"
                           :failure_reason "Failure reason"
                           :created_at "Time"})]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
