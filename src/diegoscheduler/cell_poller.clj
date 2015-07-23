(ns diegoscheduler.cell-poller
  (:require [com.stuartsierra.component :as component]
            [diegoscheduler.diego :as d]
            [clojure.core.async :refer [put! >! chan alt! go-loop]]))

(defrecord CellPoller
    [cells-from-diego schedule
     getfn
     api-url
     stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)]
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (let [cells (d/remote-resources "cells" component)]
                        (>! cells-from-diego (count cells))
                        (recur)))
          stopper :stopped))

      (assoc component :stopper stopper)))
  (stop [component]
    (when stopper (put! stopper :please-stop))
    component))

(defn new-cell-poller
  [cells-from-diego schedule
   getfn
   api-url]
  (map->CellPoller {:cells-from-diego cells-from-diego
                    :schedule schedule
                    :getfn getfn
                    :api-url api-url}))


