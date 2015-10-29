(defproject org.toomuchcode/clara-tools "0.1.0-SNAPSHOT"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [prismatic/schema "1.0.1"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [compojure "1.4.0"]
                 [org.toomuchcode/clara-rules "0.9.0-SNAPSHOT"]
                 [cljs-ajax "0.2.6"]
                 [hiccup "1.0.5"]
                 [secretary "1.2.3"]
                 [reagent "0.5.1"]]

  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :java-source-paths ["src/main/java"]

  :plugins [[lein-cljsbuild "1.1.0"]]
  :hooks [leiningen.cljsbuild]

  :cljsbuild {:builds
              [{:source-paths ["src/main/clojurescript"]
                :compiler {
                           :output-to "resources/public/js/clara-tools.js"
                           :optimizations :whitespace
                           :pretty-print true}}]}

  :scm {:name "git"
        :url "https://github.com/rbrush/clara-tools.git"}

  :pom-addition [:developers [:developer
                              [:id "rbrush"]
                              [:name "Ryan Brush"]
                              [:url "http://www.toomuchcode.org"]]]
  :deploy-repositories [["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/"
                                      :creds :gpg}]])
