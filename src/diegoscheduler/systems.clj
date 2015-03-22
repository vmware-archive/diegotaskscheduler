(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]]))

(def ^:private callback-host
  (env :vcap-app-host))
(def ^:private port
  (env :port))
(def ^:private callback-url
  (str "http://" callback-host ":" port "/taskfinished"))
(def ^:private api-url
  (env :api-url))
(def ^:private update-interval
  500)

(defn dev-system []
  (component/system-map
   :diego (new-diego update-interval api-url callback-url)
   :app (component/using
         (new-app)
         [:diego])
   :web (component/using
         (new-web-server port)
         [:app])))
