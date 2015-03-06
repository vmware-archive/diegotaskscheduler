(ns ^:figwheel-always diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)

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
