(ns diegoscheduler.systems
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [diegoscheduler.app :refer [new-app]]
            [diegoscheduler.web :refer [new-web-server]]
            [diegoscheduler.diego :refer [new-diego]])
  (:gen-class))

(def ^:private update-interval 500)

(defn main-system [host port-str api-url]
  (let [port (Integer. port-str)
        callback-url (str "http://" host ":" port "/taskfinished")]
    (component/system-map
     :diego (new-diego update-interval api-url callback-url)
     :app (component/using
           (new-app)
           [:diego])
     :web (component/using
           (new-web-server port)
           [:app]))))

(defn -main []
  (let [{:keys [vcap-app-host port api-url]} env]
    (if (and vcap-app-host port api-url)
      (component/start (main-system vcap-app-host port api-url))
      (do
        (.println *err* "ERROR: must set VCAP_APP_HOST PORT API_URL")
        (System/exit 1)))))
