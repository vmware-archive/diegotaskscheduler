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

(defn data-attrs
  [s]
  {:href (str "data:text/json;charset=utf-8," s)
   :download "rate-vs-cells.json"})

(defn pairs
  [data time-now]
  (partition 2 1 (-> data
                     (convert-all-ms-to-s :time)
                     (fill-gaps :time (/ time-now 1000)))))

(defn width
  [pairs x-interval]
  (* x-interval (count pairs)))

(defn draw
  [pairs x-interval y-scale colors]
  (let [height 100]
    [:div#chart {:style {:overflow "scroll"}}
     [:svg {:style {:background "#ccc"
                    :width (str (width pairs x-interval) "px")
                    :height (str height "px")}}
      (map-indexed (fn [idx [from to]]
                     (let [x1 (* idx x-interval)
                           x2 (+ x-interval x1)
                           rate-y1 (- height (* y-scale (:rate from)))
                           rate-y2 (- height (* y-scale (:rate to)))
                           cells-y1 (- height (* y-scale (:cell-quantity from)))
                           cells-y2 (- height (* y-scale (:cell-quantity to)))]
                       [:g {:key (str "lines" idx)}
                        [:line {:x1 x1 :y1 rate-y1
                                :x2 x2 :y2 rate-y2
                                :style {:stroke (colors :rate)}}]
                        [:line {:x1 x1 :y1 cells-y1
                                :x2 x2 :y2 cells-y2
                                :style {:stroke (colors :cell-quantity)}}]]))
                   pairs)]]))
