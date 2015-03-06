(ns ^:figwheel-always diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close!]])
    (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/ws"))]
    (if-not error
      (>! ws-channel "Hello server from client!")
      (js/console.log "Error:" (pr-str error)))))


(def task-id (atom 1))

(defn page []
  [:div
   [:h1 "Task Scheduler"]
   [:div
    [:h2 "Controls"]
    [:button#add-task {:name (str "task" @task-id)
                       :on-click #(swap! task-id inc)} "Add Task " @task-id]]
   [:div
    [:h2 "Successful Tasks"]]
   [:div
    [:h2 "Failed Tasks"]]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
