(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]]
     [clojure.string :refer [join split]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def new-task (atom {:id 1
                     :guid "task1"
                     :domain (-> (js/Math.random)
                                 (.toString 36)
                                 (.slice 2))
                     :docker-image "docker:///camelpunch/s3copier"
                     :path "/s3copier/run.sh"
                     :dir "/s3copier"
                     :args "lattices3cp-source/commonpeople.jpg lattices3cp-destination/acommoncopy.jpg"
                     :result-file "/tmp/result_file"
                     :env "AWS_ACCESS_KEY_ID=blah AWS_SECRET_ACCESS_KEY=likeidtellyouplz"}))
(defonce tasks (atom {:pending []
                      :running []
                      :successful []
                      :failed []}))
(def upch (chan))

(defn update-tasks [m new-tasks]
  (let [resolved (:resolved new-tasks)
        {failed true successful false} (group-by :failed resolved)]
    (assoc m
      :failed failed
      :successful successful
      :pending (filter #(= "PENDING" (:state %)) (:processing new-tasks))
      :running (filter #(= "RUNNING" (:state %)) (:processing new-tasks))
      )))

(defn handle-tasks [new-tasks]
  (swap! tasks update-tasks new-tasks))

(defn log-handler [body]
  (js/console.log (clj->js body)))

(def handlers
  {:tasks handle-tasks
   :log log-handler})

(defn no-handler [key message]
  (js/console.log (str "No handler defined for key: " key "\n\nMessage: " message)))

(defn route-message [message]
  (let [key (-> message keys first)]
    (if (contains? handlers key)
      ((key handlers) (key message))
      (no-handler key message))))

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
        (js/console.log "ERROR: " error "\n\n" message)
        (route-message message))
      (recur))))

(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/ws"))]
    (if error
      (js/console.log "ERROR: " (pr-str error))
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
     [:p.form-control
      [:label.lbl {:for "task_guid"} "GUID"]
      [:input#task-guid.inpt {:name "task_guid"
                              :disabled "disabled"
                              :size 9,
                              :value (guid @new-task)}]]
     (input new-task :domain "Domain")
     (input new-task :docker-image "Docker image")
     (input new-task :path "Path to executable")
     (input new-task :args "Arguments")
     (input new-task :dir "Directory")
     (input new-task :env "ENV")
     (input new-task :result-file "Result file")
     [:button.btn {:name (str "task" (:id @new-task))
                   :on-click upload-task} "Add " (guid @new-task)]]]
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
