(defproject diegoscheduler "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.danielsz/system "0.1.4"]
                 [environ "1.0.0"]
                 [http-kit "2.1.18"]
                 [clj-http "1.0.1"]
                 [compojure "1.3.2"]
                 [jarohen/chord "0.6.0"]
                 [org.clojure/clojurescript "0.0-3123"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [reagent "0.5.0-alpha3"]
                 [hiccup "1.0.5"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.5-SNAPSHOT"]
            [lein-environ "1.0.0"]]

  :source-paths ["src"]
  :test-paths ["src/test/diegoscheduler"]
  :main diegoscheduler.systems

  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.8"]
                                  [figwheel "0.2.5"]]
                   :repl-options {:init-ns user}
                   :source-paths ["dev_src"]
                   :plugins [[cider/cider-nrepl "0.8.2"]
                             [com.cemerick/austin "0.1.6"]]
                   :env {:port 8081
                         :api-url "http://192.168.11.11:8888/v1"
                         :js-url "js/compiled/diegoscheduler-debug.js"}}
             :uberjar {:main diegoscheduler.systems
                       :aot :all
                       :source-paths ["src"]
                       :env {:js-url "js/compiled/diegoscheduler.js"}}}

  :jar-exclusions [#".*-debug.js" #".*public/js/compiled/out.*"]

  :cljsbuild
  {:builds {:dev {:source-paths ["src" "dev_src"]
                  :compiler {:output-to "resources/public/js/compiled/diegoscheduler-debug.js"
                             :output-dir "resources/public/js/compiled/out"
                             :optimizations :none
                             :main diegoscheduler.dev
                             :asset-path "js/compiled/out"
                             :source-map true
                             :source-map-timestamp true
                             :cache-analysis true}}
            :prod {:source-paths ["src"]
                   :compiler {:output-to "resources/public/js/compiled/diegoscheduler.js"
                              :optimizations :advanced
                              :pretty-print false}}}}

  :figwheel {
             :http-server-root "public" ;; default and assumes "resources"
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
