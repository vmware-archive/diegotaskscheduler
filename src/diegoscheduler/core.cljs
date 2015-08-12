(ns diegoscheduler.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :as a :refer [<! >! chan close! pub put! sub timeout]]
   [clojure.string :refer [join split]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [diegoscheduler.charts :as charts]
   [diegoscheduler.tasks :as tasks]
   [diegoscheduler.sente-interop :refer [events topic]])
  (:require-macros [cljs.core.async.macros :refer [alt! go go-loop]]))

(enable-console-print!)

(def new-task
  (atom {:domain "task-scheduler"
         :log-guid "task"
         :docker-image "docker:///cdavisafc/sleepd5"
         :path "ruby"
         :dir "/app"
         :args "/app/app.rb 1"
         :result-file ""
         :env ""
         :quantity 1}))

(defonce app-state (atom {:rate 0
                          :auto-scroll true
                          :cell-quantity 0
                          :states {:queued []
                                   :running []
                                   :successful []
                                   :failed []}
                          :do-not-run #{}}))

(defonce chart-data (atom []))
(defonce y-scale (atom 5))
(defonce x-scale (atom 5))

(defn rate-vs-cell
  [m]
  (select-keys m [:cell-quantity :rate]))

(defn current-time
  []
  (-> (js/Date.) .getTime))

(defn chart-container
  []
  (.getElementById js/document "chart"))

(defn el-width
  [el]
  (if el (.-offsetWidth el) 0))

(add-watch app-state
           :chart-data
           (fn [key a old-state new-state]
             (when (not= (rate-vs-cell old-state) (rate-vs-cell new-state))
               (let [time            (current-time)
                     new-chart-data  (swap! chart-data conj
                                            (merge (rate-vs-cell new-state)
                                                   {:time time}))
                     chart-width     (charts/width (charts/pairs new-chart-data (current-time))
                                                   @x-scale)
                     container-el    (chart-container)
                     container-width (el-width container-el)]
                 (when (:auto-scroll new-state)
                   (set! (.-scrollLeft container-el)
                         (charts/scroll-position chart-width container-width)))))))

(defn chsk-url-fn
  [path {:as window-location :keys [host pathname]} websocket?]
  (if websocket?
    js/window.wsUrl
    (str "//" host (or path pathname))))

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
                                     (swap! app-state
                                            update-in [:states]
                                            tasks/add-new-state task)
                                     (recur))
            (:running events)       ([task _]
                                     (swap! app-state tasks/now-running task)
                                     (recur))
            (:successful events)    ([task _]
                                     (swap! app-state tasks/move-task task)
                                     (recur))
            (:failed events)        ([task _]
                                     (swap! app-state tasks/move-task task)
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

(defn toggle
  [a k]
  (swap! a update-in [k] not))

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

(defn section-container
  [state title content]
  (let [num-tasks (count (state (:states @app-state)))]
    [:div
     {:class (str "section " (name state) " numtasks" num-tasks)}
     [:div.section-ctr
      [:h2.sub-heading (str title " (" num-tasks ")")]
      content]]))

(defn section
  [state title task-attrs]
  [section-container 
   state title (table state (:states @app-state) task-attrs)])

(defn running-stats
  []
  (let [{:keys [rate cell-quantity]} @app-state]
    [:span (str rate " completed/s - " cell-quantity " cells")]))

(defn stringify
  [x]
  (.stringify js/JSON (clj->js x) nil 2))

(defn data-attrs
  [s filename]
  {:href (str "data:text/json;charset=utf-8," s)
   :download filename})

(defn page
  []
  [:div.container
   [:h1.heading
    "Task Scheduler ("
    [running-stats]
    ")"]
   [:div.fw-section
    (let [colors               {:rate "#000" :cell-quantity "#f00"}
          pairs                (charts/pairs @chart-data (current-time))
          chart-logical-width  (charts/width pairs @x-scale)
          container-width      (el-width (chart-container))
          chart-draw-width     (+ (charts/scroll-position chart-logical-width container-width)
                                  container-width)]
      [:div.section-ctr
       [charts/draw
        (charts/pairs @chart-data (current-time))
        @x-scale @y-scale
        chart-draw-width
        colors]
       [:p.inl
        [:a (data-attrs (stringify @chart-data) "rate-vs-cells.json") "Download JSON"]]
       [:p.inl {:style {:color (colors :cell-quantity)}} "Cells"]
       [:p.inl {:style {:color (colors :rate)}} "Rate"]
       [:p.inl
        [:input#autoscroll {:type "checkbox"
                            :checked (:auto-scroll @app-state)
                            :on-change #(toggle app-state :auto-scroll)}]
        [:label {:for "autoscroll"} "Auto-scroll"]]])
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
