(defproject org.toomuchcode/clara-tools "0.2.0-SNAPSHOT"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [prismatic/schema "1.0.1"]
                 [compojure "1.4.0"]
                 [org.toomuchcode/clara-rules "0.9.0"]
                 [cljs-ajax "0.2.6"]
                 [hiccup "1.0.5"]
                 [secretary "1.2.3"]
                 [reagent "0.5.1"]
                 [org.clojure/core.cache "0.6.4"]
                 [cljsjs/react-bootstrap "0.25.1-0"]
                 [http-kit "2.1.19"]
                 [com.cognitect/transit-cljs "0.8.232"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [ring/ring-core "1.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [prismatic/schema "1.0.3"]
                 [figwheel-sidecar "0.4.0"]]

  :resource-paths ["resources" "target/resources"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :java-source-paths ["src/main/java"]
  :lein-release {:deploy-via :clojars}
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.4.1"]]
  :hooks [leiningen.cljsbuild]

  :figwheel {:ring-handler clara.tools.impl.server/app
             :nrepl-port 7888}

  :cljsbuild {:builds
              [{:source-paths ["src/main/clojurescript"]
                :figwheel true
                :externs ["externs.js"]
                :compiler {
                           :main clara.tools.client.main
                           :asset-path "/js"
;;                           :output-to "raesources/public/js/clara-tools.js"
                           :output-dir "target/resources/public/js"
                           :optimizations :none
                           :pretty-print true}}]}

  :scm {:name "git"
        :url "https://github.com/rbrush/clara-tools"}

  :pom-addition [:developers [:developer
                              [:id "rbrush"]
                              [:name "Ryan Brush"]
                              [:url "http://www.toomuchcode.org"]]]
  :deploy-repositories [["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/"
                                      :creds :gpg}]])
