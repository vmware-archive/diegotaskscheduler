(ns diegoscheduler.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :as a :refer [<! >! chan close! pub put! sub]]
   [clojure.string :refer [join split]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [diegoscheduler.charts :as charts])
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
                          :cell-quantity 0
                          :states {:queued []
                                   :running []
                                   :successful []
                                   :failed []}
                          :do-not-run #{}}))

(defonce chart-data (atom []))
(defonce scale (atom 5))

(defn rate-vs-cell
  [m]
  (select-keys m [:cell-quantity :rate]))

(defn current-time
  []
  (-> (js/Date.) .getTime))

(add-watch app-state
           :chart-data
           (fn [key a old-state new-state]
             (when (not= (rate-vs-cell old-state) (rate-vs-cell new-state))
               (swap! chart-data conj
                      (merge (rate-vs-cell new-state)
                             {:time (current-time)})))))

(defn same-guid-as
  [m]
  #(= (:task_guid m) (:task_guid %)))

(defn remove-old-state
  [m task-update]
  (reduce (fn [acc [state tasks]]
            (merge acc
                   {state (vec (remove (same-guid-as task-update) tasks))}))
          {} m))

(defn state-of
  [task-update]
  (if (:failed task-update)
    :failed
    (case (:state task-update)
      "COMPLETED" :successful
      "RUNNING" :running
      "PENDING" :pending
      "QUEUED" :queued)))

(defn add-new-state
  [m task-update]
  (update-in m [(state-of task-update)] conj task-update))

(defn do-not-run
  [m task]
  (update-in m [:do-not-run] conj (:task_guid task)))

(defn move-task
  [m task-update]
  (-> m
      (do-not-run task-update)
      (update-in [:states] remove-old-state task-update)
      (update-in [:states] add-new-state task-update)))

(defn now-running
  [m task]
  (if (some #{(:task_guid task)} (:do-not-run m))
    m
    (move-task m task)))

(defn chsk-url-fn
  [path {:as window-location :keys [host pathname]} websocket?]
  (if websocket?
    js/window.wsUrl
    (str "//" host (or path pathname))))

(def extract-data
  (map (fn [{[_ [_ data]] :event}] data)))

(def events
  "Map of type keywords to channels that automatically extract the
  data portion of a sente event"
  (into {}
        (map (fn [type] {type (chan 1 extract-data)}))
        [:queued :running :successful :failed :rate :cell-quantity]))

(defn task-topic
  [{[_ [_ data]] :event}]
  (state-of data))

(defn app-topic
  [{[_ [event-type _]] :event :as e}]
  (case event-type
    :diegotaskscheduler/rate :rate
    :diegotaskscheduler/cell-quantity :cell-quantity
    (task-topic e)))

(defn topic
  "Return topic keyword for a given sente event"
  [{[id _] :event :as e}]
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
            (:queued events)        ([task _]
                                     (println "Queued:" (:task_guid task))
                                     (swap! app-state
                                            update-in [:states] add-new-state task)
                                     (recur))
            (:running events)       ([task _]
                                     (println "Running:" (:task_guid task))
                                     (swap! app-state now-running task)
                                     (recur))
            (:successful events)    ([task _]
                                     (println "Successful:" (:task_guid task))
                                     (swap! app-state move-task task)
                                     (recur))
            (:failed events)        ([task _]
                                     (println "Failed:" (:task_guid task))
                                     (swap! app-state move-task task)
                                     (recur))
            (:rate events)          ([rate _]
                                     (swap! app-state assoc :rate rate)
                                     (recur))
            (:cell-quantity events) ([x _]
                                     (swap! app-state assoc :cell-quantity x)
                                     (recur))
            ))))

(defn upload-task
  []
  (chsk-send! [:diegotaskscheduler/task @new-task]))

(defn event-update
  [a attr]
  (fn [e]
    (swap! a #(assoc % attr (-> e .-target .-value)))))

(defn input
  [a key label]
  (let [id (str "task-" (name key))]
    [:p.form-control
     [:label.lbl {:for id} label]
     [:input.inpt {:id id
                   :size 30
                   :value (key (deref a))
                   :on-change (event-update a key)}]]))

(defn short-cmd
  [t]
  (let [run (get-in t [:action :run])]
    (join " " (cons (-> (:path run) (split #"/") last)
                    (:args run)))))

(defn table-division
  [keyfn k t]
  [:td.tbldv {:key (str keyfn k)}
   (case k
     :created_at (.toTimeString (js/Date. (/ (:created_at t) 1000000)))
     :rootfs (last (clojure.string/split (k t) #"/"))
     :cmd (short-cmd t)
     (k t))])

(defn table
  [keyfn coll fields]
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

(defn section
  [state title task-attrs]
  (let [num-tasks (count (state (:states @app-state)))]
    [:div
     {:class (str "section " (name state) " numtasks" num-tasks)}
     [:div.section-ctr
      [:h2.sub-heading (str title " (" num-tasks ")")]
      (table state (:states @app-state) task-attrs)]]))

(defn stringify
  [x]
  (.stringify js/JSON (clj->js x) nil 2))

(defn data-attrs
  []
  (let [data (str "text/json;charset=utf-8," (stringify @chart-data))]
    {:href (str "data:" data) :download "rate-vs-cells.json"}))

(defn running-stats
  []
  (let [{:keys [rate cell-quantity]} @app-state]
    [:span (str rate " completed/s - " cell-quantity " cells")]))

(defn chart
  []
  (let [pairs (partition 2 1 (charts/fill-gaps @chart-data (/ (current-time) 1000)))
        interval-x 5
        multiplier @scale
        height 100]
    [:div.section-ctr
     [:div {:style {:overflow "scroll"}}
      [:svg {:style {:background "#ccc" :width "10000px" :height (str height "px")}}
       (map-indexed (fn [idx [from to]]
                      (let [x1 (* idx interval-x)
                            y1 (- height (* multiplier (:rate from)))
                            x2 (+ interval-x (* idx interval-x))
                            y2 (- height (* multiplier (:rate to)))]
                        [:line {:key (str idx (map :rate [from to]))
                                :x1 x1 :y1 y1
                                :x2 x2 :y2 y2
                                :style {:stroke "#000"}}]))
                    pairs)]]
     [:a (data-attrs) "Download JSON"]]))

(defn page
  []
  [:div.container
   [:h1.heading
    "Task Scheduler ("
    [running-stats]
    ")"]
   [:div.fw-section
    [chart]
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
                                :rootfs "Docker image"
                                :cell_id "Cell"})
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
