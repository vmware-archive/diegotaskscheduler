(ns user
  (:require [reloaded.repl :refer [system init start stop go reset set-init!]]
            [clojure.core.async :refer [put! chan buffer sliding-buffer >!! <!!]]
            [diegoscheduler.systems :refer [main-system]]
            [clojure.tools.namespace.repl :refer [refresh clear set-refresh-dirs]]
            [org.httpkit.server :as http-kit]
            [diegoscheduler.diego :as diego]
            [environ.core :refer [env]]
            [leiningen.clean :refer [delete-file-recursively]]
            [cljs.build.api :as js])
  (:import [java.net InetAddress]))

(def js-dir "resources/public/js")

(defn development-build []
  (delete-file-recursively js-dir :silently)
  (js/build "src"
            {:main 'diegoscheduler.core
             :output-to (str js-dir "/application.js")
             :output-dir js-dir
             :asset-path "js"}))

(defn production-build []
  (delete-file-recursively js-dir :silently)
  (js/build "src"
            {:main 'diegoscheduler.core
             :output-to (str js-dir "/application.js")
             :output-dir js-dir
             :asset-path "js"
             :externs ["dev_src/externs.js"]
             :optimizations :advanced}))

(def local-ip
  (->> (InetAddress/getLocalHost)
       .toString
       (re-seq #"\d+.\d+\.\d+\.\d+")
       first))

(set-init! #(main-system (:port env)
                         (:api-url env)
                         "ws://localhost:8081/ws"))

(def task-id (atom 1))

(comment
  (refresh)
  (clear)

  (go)
  (stop)
  (reset)

  (development-build)
  (production-build)
  (cemerick.piggieback/cljs-repl (cljs.repl.rhino/repl-env))

  (:web system)

  (count (diego/remote-tasks (:diego system)))
  (map keys (into (sorted-map) (diego/remote-tasks))))
