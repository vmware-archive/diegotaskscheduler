(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            (system.components
             [http-kit :refer [new-web-server]])
            [diegoscheduler.server :as server]
            [diegoscheduler.diego :as diego]
            ))

(defn dev-system []
  (component/system-map
   :web (new-web-server 8080 server/app)))
