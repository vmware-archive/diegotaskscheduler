(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego-updater :refer [new-diego-updater]]))

(def ^:private callback-url
  "http://192.168.1.3:8081/taskfinished")

(defn dev-system []
  (component/system-map
   :updater (new-diego-updater 500)
   :app (component/using
         (new-app callback-url)
         [:updater])
   :web (component/using
         (new-web-server 8081)
         [:app])))
