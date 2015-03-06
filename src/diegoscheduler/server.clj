(ns diegoscheduler.server
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]])
  (:gen-class))

(defroutes app
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(defn -main []
  (http-kit/run-server app {:port 8080}))
