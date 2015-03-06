(ns diegoscheduler.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [chord.client :refer [ws-ch]]
     [cljs.core.async :refer [<! >! put! close! chan]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def task-id (atom 1))
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
            (recur))))
      )
    ))

(defn task-inc []
  (swap! task-id inc)
  (put! upch (str "Sending task " @task-id)))

(defn page []
  [:div
   [:h1 "Task Scheduler"]
   [:div
    [:h2 "Controls"]
    [:button#add-task {:name (str "task" @task-id)
                       :on-click task-inc} "Add Task " @task-id]]
   [:div
    [:h2 "Successful Tasks"]]
   [:div
    [:h2 "Failed Tasks"]]])

(reagent/render-component [page]
                          (. js/document (getElementById "app")))
