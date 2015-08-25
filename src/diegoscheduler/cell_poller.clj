(ns diegoscheduler.cell-poller
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! >! chan alt! go-loop]]))

(defrecord CellPoller
    [cells-from-diego schedule getfn api-url stopper]
  component/Lifecycle
  (start [component]
    (let [stopper (chan)
          all-cells (chan)]
      (go-loop []
        (alt!
          (schedule) ([_ _]
                      (getfn (str api-url "/cells") all-cells)
                      (recur))
          all-cells  ([cells _]
                      (put! cells-from-diego (count cells))
                      (recur))
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


