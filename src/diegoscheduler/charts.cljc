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

(defn stringify
  [x]
  #?(:cljs (.stringify js/JSON (clj->js x) nil 2)))

(defn draw
  [data time-now y-scale]
  (let [pairs (partition 2 1
                         (-> data
                             (convert-all-ms-to-s :time)
                             (fill-gaps :time (/ time-now 1000))))
        interval-x 5
        multiplier y-scale
        height 100
        colors {:rate "#000" :cell-quantity "#f00"}]
    [:div.section-ctr
     [:div {:style {:overflow "scroll"}}
      [:svg {:style {:background "#ccc" :width "10000px" :height (str height "px")}}
       (map-indexed (fn [idx [from to]]
                      (let [x1 (* idx interval-x)
                            x2 (+ interval-x (* idx interval-x))
                            rate-y1 (- height (* multiplier (:rate from)))
                            rate-y2 (- height (* multiplier (:rate to)))
                            cells-y1 (- height (* multiplier (:cell-quantity from)))
                            cells-y2 (- height (* multiplier (:cell-quantity to)))]
                        [:g {:key (str "lines" idx)}
                         [:line {:x1 x1 :y1 rate-y1
                                 :x2 x2 :y2 rate-y2
                                 :style {:stroke (colors :rate)}}]
                         [:line {:x1 x1 :y1 cells-y1
                                 :x2 x2 :y2 cells-y2
                                 :style {:stroke (colors :cell-quantity)}}]]))
                    pairs)]]
     [:p.inl
      [:a (data-attrs (stringify data)) "Download JSON"]]
     [:p.inl {:style {:color (colors :cell-quantity)}} "Cells"]
     [:p.inl {:style {:color (colors :rate)}} "Rate"]]))
