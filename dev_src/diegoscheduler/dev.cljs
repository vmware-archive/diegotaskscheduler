(ns diegoscheduler.dev
    (:require
     [diegoscheduler.core]
     [figwheel.client :as fw]))

(fw/start {
  :websocket-url "ws://localhost:8080/figwheel-ws"
  :on-jsload (fn []
               ;; (stop-and-start-my app)
               )})
