(ns diegoscheduler.charts)
(defn- add-end-of-range
  [coll end-time]
  (conj coll {:time (inc end-time)}))

(defn- multi-update
  [coll k & args]
  (reduce (fn [acc m]
            (conj acc (apply update-in m [k] args)))
          []
          coll))

(defn- ms-to-s
  [coll k]
  (multi-update coll k #(-> % (/ 1000) int)))

(defn fill-gaps
  [coll end-time]
  (reduce (fn [slots [{from-time :time from-rate :rate from-cell-quantity :cell-quantity}
                      {  to-time :time   to-rate :rate   to-cell-quantity :cell-quantity}]]
            (into slots
                  (for [time (range from-time to-time)]
                    {:time time :rate from-rate :cell-quantity from-cell-quantity})))
          []
          (partition 2 1 (add-end-of-range (ms-to-s coll :time) end-time))))
