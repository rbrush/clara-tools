(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'org.toomuchcode/clara-tools)
(def version "0.3.0-SNAPSHOT")
(def class-dir "target/classes")
(def ui-dir "target/resources")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target/classes"}))

(defn- sh! [& args]
  (let [{:keys [exit]} (b/process {:command-args args})]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (pr-str args)) {:exit exit})))))

(defn- compile-ui!
  "Builds the release UI into target/resources. Needs npm dependencies
   installed (`npm ci`)."
  []
  ;; Start clean so no stale output ends up in the jar.
  (b/delete {:path (str ui-dir "/public")})
  (b/copy-file {:src "node_modules/bootstrap/dist/css/bootstrap.min.css"
                :target (str ui-dir "/public/css/bootstrap.min.css")})
  (sh! "npx" "shadow-cljs" "release" "app"))

(defn jar [_]
  (clean nil)
  (compile-ui!)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src/main/clojure"]
                :scm {:url "https://github.com/rbrush/clara-tools"
                      :connection "scm:git:git://github.com/rbrush/clara-tools.git"
                      :developerConnection "scm:git:ssh://git@github.com/rbrush/clara-tools.git"}
                :pom-data [[:description "Experimental tooling for exploring and working with Clara-based rulesets."]
                           [:url "https://github.com/rbrush/clara-tools"]
                           [:licenses
                            [:license
                             [:name "Eclipse Public License"]
                             [:url "http://www.eclipse.org/legal/epl-v10.html"]]]
                           [:developers
                            [:developer
                             [:id "rbrush"]
                             [:name "Ryan Brush"]
                             [:url "http://www.toomuchcode.org"]]]]})
  (b/copy-dir {:src-dirs ["src/main/clojure" "resources"]
               :target-dir class-dir})
  ;; Ship the compiled UI so consumers don't need a ClojureScript build.
  (b/copy-dir {:src-dirs [ui-dir]
               :target-dir class-dir
               :include "public/**"
               :ignores [#"manifest\.edn"]})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
