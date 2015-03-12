(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce new-task (atom {:id 1
                         :guid "task1"
                         :domain (str "domain-" (js/Math.random))
                         :docker-image "docker://camelpunch/s3copier"
                         :path "/usr/local/bundle/bin/bundle"
                         :args "exec ./copy.rb mysource mydest"}))
(defonce tasks (atom {:processing []
                      :successful []
                      :failed []}))
(def upch (chan))

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
          (when-let [{message :message
                      error :error} (<! ws-channel)]
            (if error
              (js/console.log (str "ERROR: " error))
              (route-message message))
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
    [:h2 "Processing Tasks"]
    [:table
     [:thead
      [:tr
       [:th "GUID"]
       [:th "State"]
       [:th "Cell"]
       [:th "Docker image"]
       [:th "Time"]]]
     [:tbody
      (for [t (sort-by :created_at > (:processing @tasks))]
        ^{:key t}
        [:tr
         [:td (:task_guid t)]
         [:td (:state t)]
         [:td (:cell_id t)]
         [:td (:rootfs t)]
         [:td (.toTimeString (js/Date. (/ (:created_at t) 1000000)))]])]]]
   [:div
    [:h2 "Successful Tasks"]
    [:table
     [:thead
      [:tr
       [:th "GUID"]
       [:th "State"]
       [:th "Cell"]
       [:th "Docker image"]
       [:th "Time"]]]
     [:tbody
      (for [t (sort-by :created_at > (:successful @tasks))]
        ^{:key t}
        [:tr
         [:td (:task_guid t)]
         [:td (:state t)]
         [:td (:cell_id t)]
         [:td (:rootfs t)]
         [:td (.toTimeString (js/Date. (/ (:created_at t) 1000000)))]])]]]
   [:div
    [:h2 "Failed Tasks"]
    [:table
     [:thead
      [:tr
       [:th "GUID"]
       [:th "Domain"]
       [:th "State"]
       [:th "Cell"]
       [:th "Docker image"]
       [:th "Failure reason"]
       [:th "Time"]]]
     [:tbody
      (for [t (sort-by :created_at > (:failed @tasks))]
        ^{:key t}
        [:tr
         [:td (:task_guid t)]
         [:td (:domain t)]
         [:td (:state t)]
         [:td (:cell_id t)]
         [:td (:rootfs t)]
         [:td (:failure_reason t)]
         [:td (.toTimeString (js/Date. (/ (:created_at t) 1000000)))]])]]]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
