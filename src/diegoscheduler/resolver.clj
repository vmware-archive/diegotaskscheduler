(ns diegoscheduler.resolver
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go-loop chan <! alt!]]))

(defrecord Resolver
    [tasks-to-resolve
     deletefn
     api-url]
  component/Lifecycle
  (start [component]
    (let [response (chan)]
      (go-loop []
        (alt!
          tasks-to-resolve ([task _]
                            (deletefn (str api-url "/tasks/" (:task_guid task)) response)
                            (recur))
          response         ([_ _]
                            (recur)))))
    component)
  (stop [component]
    component))

(defn new-resolver [tasks-to-resolve deletefn api-url]
  (map->Resolver {:tasks-to-resolve tasks-to-resolve
                  :deletefn deletefn
                  :api-url api-url}))
