(ns diegoscheduler.charts)

(defn convert-all-ms-to-s
  "Divides all ks in coll by 1000, and floors."
  [coll k]
  (map (fn [m] (update-in m [k] (comp #(/ % 1000) int)))
       coll))

(defn fill-gaps
  "Given a collection of maps, a key in the maps representing a
  monotonically increasing series, and a value for a highest value in
  that series, pad out missing maps with values from previous ones."
  [coll k final-k]
  (reduce (fn [ticks [from to]]
            (into ticks
                  (for [v (range (from k) (to k))]
                    (merge {k v} (dissoc from k)))))
          []
          (partition 2 1 (conj coll {k (inc final-k)}))))
