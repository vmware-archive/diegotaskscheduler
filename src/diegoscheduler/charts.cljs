(ns diegoscheduler.charts)

(defn- add-end-of-range
  [coll end-time]
  (conj coll {:time (inc end-time)}))

(defn fill-gaps
  [coll end-time]
  (reduce (fn [slots [{from-time :time from-rate :rate}
                      {to-time :time to-rate :rate}]]
            (let [from-rates (into slots
                                   (for [time (range from-time to-time)]
                                     {:time time :rate from-rate}))]
              from-rates))
          []
          (partition 2 1 (add-end-of-range coll end-time))))
