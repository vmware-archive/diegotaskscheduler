(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego-updater :refer [new-diego-updater]]))

(defn dev-system []
  (component/system-map
   :updater (new-diego-updater 500)
   :app (component/using
         (new-app)
         [:updater])
   :web (component/using
         (new-web-server 8080)
         [:app])))
