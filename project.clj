(defproject diegoscheduler "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.danielsz/system "0.1.4"]
                 [environ "1.0.0"]
                 [http-kit "2.1.18"]
                 [clj-http "1.0.1"]
                 [compojure "1.3.4"]
                 [jarohen/chord "0.6.0"]
                 [org.clojure/clojurescript "0.0-3123" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [reagent "0.5.0-alpha3"]
                 [hiccup "1.0.5"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-environ "1.0.0"]]

  :hooks [leiningen.cljsbuild]

  :source-paths ["src"]
  :test-paths ["test"]
  :main diegoscheduler.systems

  :env {:js-url "js/compiled/diegoscheduler.js"}

  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]
                                  [org.clojure/tools.namespace "0.2.10"]]
                   :repl-options {:init-ns user}
                   :source-paths ["dev_src"]
                   :plugins [[cider/cider-nrepl "0.9.0-SNAPSHOT"]
                             [com.cemerick/austin "0.1.6"]]
                   :env {:port 8081
                         :api-url "http://192.168.11.11:8888/v1"
                         :js-url "js/compiled/diegoscheduler-debug.js"}}
             :uberjar {:main diegoscheduler.systems
                       :aot [diegoscheduler.app
                             diegoscheduler.diego
                             diegoscheduler.http
                             diegoscheduler.pages
                             diegoscheduler.systems
                             diegoscheduler.web]}}

  :jar-exclusions [#".*-debug.js" #".*public/js/compiled/out.*"]

  :cljsbuild
  {:builds {:dev {:source-paths ["src"]
                  :compiler {:output-to "resources/public/js/compiled/diegoscheduler-debug.js"
                             :output-dir "resources/public/js/compiled/out"
                             :optimizations :none
                             :asset-path "js/compiled/out"
                             :source-map true
                             :source-map-timestamp true
                             :cache-analysis true}}
            :prod {:source-paths ["src"]
                   :compiler {:output-to "resources/public/js/compiled/diegoscheduler.js"
                              :optimizations :advanced
                              :pretty-print false}}}})
