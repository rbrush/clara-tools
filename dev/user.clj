(ns user
  "Loaded at startup by `mise run dev`: runs shadow-cljs in this JVM so the UI
   hot-reloads while the REPL is up.")

(defn watch-ui!
  "Starts the shadow-cljs server and watches the UI build."
  []
  ((requiring-resolve 'shadow.cljs.devtools.server/start!))
  ((requiring-resolve 'shadow.cljs.devtools.api/watch) :app))

(try
  (watch-ui!)
  (catch Exception e
    (println "Unable to start shadow-cljs:" (ex-message e))))
