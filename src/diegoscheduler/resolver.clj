(ns diegoscheduler.resolver
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go-loop pipeline close! <!]]))

(defrecord Resolver
    [tasks-to-resolve
     deletefn
     api-url]
  component/Lifecycle
  (start [component]
    (go-loop []
      (when-let [task (<! tasks-to-resolve)]
        (deletefn (str api-url "/tasks/" (:task_guid task)))
        (recur)))
    component)
  (stop [component]
    component))

(defn new-resolver [tasks-to-resolve deletefn api-url]
  (map->Resolver {:tasks-to-resolve tasks-to-resolve
                  :deletefn deletefn
                  :api-url api-url}))
