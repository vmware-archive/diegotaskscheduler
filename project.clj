(defproject diegoscheduler "0.3.0-SNAPSHOT"
  :description "Experimental task scheduler for Diego"
  :url "https://github.com/pivotal-cf-experimental/diegotaskscheduler"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.danielsz/system "0.1.8"]
                 [environ "1.0.0"]
                 [http-kit "2.1.18"]
                 [clj-http "1.1.2" :exclusions [org.clojure/tools.reader]]
                 [compojure "1.3.4"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [com.taoensso/sente "1.5.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [reagent "0.5.0"]
                 [hiccup "1.0.5"]
                 [leiningen "2.5.1"]
                 [org.clojure/tools.logging "0.3.1"]]

  :plugins [[lein-environ "1.0.0"]
            [com.cemerick/clojurescript.test "0.3.3"]]

  :source-paths ["src"]
  :test-paths ["test"]
  :main diegoscheduler.systems

  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :source-paths ["dev_src"]
                   :env {:port 8081}}
             :uberjar {:main diegoscheduler.systems
                       :aot [diegoscheduler.diego
                             diegoscheduler.cell-poller
                             diegoscheduler.task-poller
                             diegoscheduler.task-submitter
                             diegoscheduler.rate-emitter
                             diegoscheduler.resubmitter
                             diegoscheduler.http
                             diegoscheduler.pages
                             diegoscheduler.systems
                             diegoscheduler.web]}}

  :uberjar-exclusions [#"^goog/.*$"
                       #"^com/google/javascript/.*"
                       #"^public/js/chord/.*"
                       #"^public/js/cljs/.*"
                       #"^public/js/clojure/.*"])
