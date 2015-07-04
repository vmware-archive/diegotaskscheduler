(ns diegoscheduler.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :as a :refer [<! >! chan close! pub put! sub]]
   [clojure.string :refer [join split]]
   [taoensso.sente :as sente :refer [cb-success?]])
  (:require-macros [cljs.core.async.macros :refer [alt! go go-loop]]))

(enable-console-print!)

(def new-task
  (atom {:domain "task-scheduler"
         :log-guid "task"
         :docker-image "docker:///cdavisafc/sleepd5"
         :path "ruby"
         :dir "/app"
         :args "app.rb 1"
         :result-file ""
         :env ""
         :quantity 1}))

(defonce app-state (atom {:rate 0
                          :states {:pending []
                                   :running []
                                   :successful []
                                   :failed []}}))
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
      "PENDING" :pending
      "QUEUED" :queued)))

(defn add-new-state [m task-update]
  (update-in m [(state-of task-update)] conj task-update))

(defn handle-task-update [m task-update]
  (-> m
      (update-in [:states] remove-old-state task-update)
      (update-in [:states] add-new-state task-update)))

(defn handle-task [task-update]
  (swap! app-state handle-task-update task-update))

(defn chsk-url-fn
  [path {:as window-location :keys [host pathname]} websocket?]
  (if websocket?
    js/window.wsUrl
    (str "//" host (or path pathname))))

(def extract-data
  (map (fn [{[_ [_ data]] :event}] data)))

(def events
  {:pending (chan 1 extract-data)
   :queued (chan 1 extract-data)
   :running (chan 1 extract-data)
   :successful (chan 1 extract-data)
   :failed (chan 1 extract-data)
   :rate (chan 1 extract-data)})

(defn task-topic
  [{[id [event data]] :event :as e}]
  (state-of data))

(defn app-topic
  [{[id [event data]] :event :as e}]
  (if (= :diegotaskscheduler/rate event)
    :rate
    (task-topic e)))

(defn topic
  [{[id event-data] :event :as e}]
  (if (= :chsk/recv id)
    (app-topic e)
    :connection))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ws" {:type :auto
                                         :chsk-url-fn chsk-url-fn})
      publication (pub ch-recv topic)]
  (doseq [[tpc c] events]
    (sub publication tpc c))
  (def chsk chsk)
  (def chsk-send! send-fn)
  (def chsk-state state))

(set! (.-onload js/window)
      (fn []
        (go-loop []
          (alt!
            (:queued events)     ([task _]
                                  (println (:state task) (:task_guid task))
                                  (handle-task task)
                                  (recur))
            (:running events)    ([task _]
                                  (println (:state task) (:task_guid task))
                                  (handle-task task)                                  
                                  (recur))
            (:successful events) ([task _]
                                  (println (:state task) (:task_guid task))
                                  (handle-task task)                                  
                                  (recur))
            (:failed events)     ([task _]
                                  (println (:state task) (:task_guid task))
                                  (handle-task task)                                  
                                  (recur))
            (:rate events)       ([rate _]
                                  (swap! app-state assoc :rate rate)
                                  (recur))
            ))))

(defn upload-task []
  (chsk-send! [:diegotaskscheduler/task @new-task]))

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
  (let [num-tasks (count (state (:states @app-state)))]
    [:div
     {:class (str "section " (name state) " numtasks" num-tasks)}
     [:div.section-ctr
      [:h2.sub-heading (str title " (" num-tasks ")")]
      (table state (:states @app-state) task-attrs)]]))

(defn page []
  [:div.container
   [:h1.heading (str "Task Scheduler (" (:rate @app-state) " completed/s)")]
   [:div.fw-section
    [:div.section-ctr
     [:h2.sub-heading "Controls"]
     (input new-task :domain "Domain")
     (input new-task :log-guid "Log GUID")
     (input new-task :docker-image "Docker image")
     (input new-task :path "Path to executable")
     (input new-task :args "Arguments")
     (input new-task :dir "Directory")
     (input new-task :env "ENV")
     (input new-task :result-file "Result file")
     (input new-task :quantity "Quantity")
     [:button.btn {:on-click upload-task} "Add Task"]]]
   (section :queued "Queued" {:task_guid "GUID"
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
