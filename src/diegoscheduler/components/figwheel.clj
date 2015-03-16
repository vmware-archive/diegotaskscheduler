(ns diegoscheduler.components.figwheel
  (:require [com.stuartsierra.component :as component]
            [figwheel-sidecar.auto-builder :as fig-auto]
            [figwheel-sidecar.core :as fig]
            [clojurescript-build.auto :as auto]))

(defrecord Figwheel [server builder]
  component/Lifecycle
  (start [component]
    (let [server (fig/start-server { :css-dirs ["resources/public/css"]})
          config {:builds [{:id "dev"
                            :source-paths ["src" "dev_src"]
                            :compiler {:output-to "resources/public/js/compiled/diegoscheduler.js"
                                       :output-dir "resources/public/js/compiled/out"
                                       :optimizations :none
                                       :asset-path "js/compiled/out"
                                       :source-map true
                                       :source-map-timestamp true
                                       :cache-analysis true }}]
                  :figwheel-server server}
          builder (fig-auto/autobuild* config)]
      (assoc component
             :server server
             :builder builder)))
  (stop [component]
    (when builder
      (auto/stop-autobuild! builder))
    (when server
      (fig/stop-server server))
    component))

(defn new-figwheel-server []
  (map->Figwheel {}))
