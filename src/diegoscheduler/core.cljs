(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]]
     [clojure.string :refer [join split]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def new-task
  (atom {:domain "task-scheduler"
         :docker-image "docker:///camelpunch/s3copier"
         :path "/app/run.sh"
         :dir "/app"
         :args "lattices3cp-source/commonpeople.jpg lattices3cp-destination/acommoncopy.jpg"
         :result-file "/tmp/result_file"
         :env "AWS_ACCESS_KEY_ID=blah AWS_SECRET_ACCESS_KEY=likeidtellyouplz"}))

(defonce tasks (atom {:pending []
                      :running []
                      :successful []
                      :failed []}))
(def uploads (chan))

(defn same-guid-as [m]
  #(= (:task_guid m) (:task_guid %)))

(defn remove-old-state [m task-update]
  (reduce (fn [acc [state tasks]]
            (merge acc
                   {state (vec (remove (same-guid-as task-update) tasks))}))
          {} m))

(defn state-of [task-update]
  (if (:failed task-update)
    :failed
    (case (:state task-update)
      "COMPLETED" :successful
      "RUNNING" :running
      "PENDING" :pending)))

(defn add-new-state [m task-update]
  (update-in m [(state-of task-update)] conj task-update))

(defn handle-task-update [m task-update]
  (-> m
      (remove-old-state task-update)
      (add-new-state task-update)))

(defn handle-task [task-update]
  (swap! tasks handle-task-update task-update))

(defn handle-outgoing [server]
  (go-loop []
    (when-let [msg (<! uploads)]
      (>! server msg)
      (recur))))

(defn handle-incoming [server]
  (go-loop []
    (when-let [{message :message
                error :error} (<! server)]
      (if error
        (js/console.log "ERROR: " error "\n\n" message)
        (handle-task message))
      (recur))))

(set! (.-onload js/window)
      (fn []
        (go
          (let [{:keys [ws-channel error]} (<! (ws-ch js/window.wsUrl))]
            (if error
              (js/console.log "ERROR: " (pr-str error))
              (do
                (handle-outgoing ws-channel)
                (handle-incoming ws-channel)))))))

(defn upload-task []
  (put! uploads @new-task))

(defn event-update [a attr]
  (fn [e]
    (swap! a #(assoc % attr (-> e .-target .-value)))))

(defn input [a key label]
  (let [id (str "task-" (name key))]
    [:p.form-control
     [:label.lbl {:for id} label]
     [:input.inpt {:id id
                   :size 30
                   :value (key (deref a))
                   :on-change (event-update a key)}]]))

(defn short-cmd [t]
  (let [run (get-in t [:action :run])]
    (join " " (cons (-> (:path run) (split #"/") last)
                    (:args run)))))

(defn table-division [keyfn k t]
  [:td.tbldv {:key (str keyfn k)}
   (case k
     :created_at (.toTimeString (js/Date. (/ (:created_at t) 1000000)))
     :rootfs (last (clojure.string/split (k t) #"/"))
     :cmd (short-cmd t)
     (k t))])

(defn table [keyfn coll fields]
  [:div.tblctr
   [:table
    [:thead
     [:tr
      (for [heading (vals fields)]
        [:th.tblhd {:key (str keyfn heading)} heading])]]
    [:tbody
     (for [t (sort-by :created_at > (keyfn coll))]
       [:tr {:key (str keyfn t)}
        (for [k (keys fields)]
          (table-division keyfn k t))])]]])

(defn section [state title task-attrs]
  [:div
   {:class (str "section " (name state) " numtasks" (count (state @tasks)))}
   [:div.section-ctr
    [:h2.sub-heading title]
    (table state @tasks task-attrs)]])

(defn page []
  [:div.container
   [:h1.heading "Task Scheduler"]
   [:div.fw-section
    [:div.section-ctr
     [:h2.sub-heading "Controls"]
     (input new-task :domain "Domain")
     (input new-task :docker-image "Docker image")
     (input new-task :path "Path to executable")
     (input new-task :args "Arguments")
     (input new-task :dir "Directory")
     (input new-task :env "ENV")
     (input new-task :result-file "Result file")
     [:button.btn {:on-click upload-task} "Add Task"]]]
   (section :pending "Pending" {:task_guid "GUID"
                                :domain "Domain"
                                :rootfs "Docker image"})
   (section :running "Running" {:task_guid "GUID"
                                :domain "Domain"
                                :rootfs "Docker image"})
   (section :successful "Successful" {:task_guid "GUID"
                                      :domain "Domain"
                                      :rootfs "Docker image"
                                      :result "Result"})
   (section :failed "Failed" {:task_guid "GUID"
                              :domain "Domain"
                              :rootfs "Docker image"
                              :failure_reason "Failure reason"
                              :cmd "Command"})])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
