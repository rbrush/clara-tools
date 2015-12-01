(ns clara.tools.watch
  "This namespace supports watching Clara sessions and updates queries as session changes."
  (:require [clara.tools.impl.watcher :as w]
            [clara.tools.impl.server :as s]
            [clara.rules :as r]
            [clojure.java.browse :as b]))

(defmacro mk-watched-session
  "Creates a watch Clara session. The first argument is a name to be dispayed to the client, and the following
   arguments are simply passed to clara.rules/mk-session."
  [name & args]
  {:pre [(string? name)]}
  (let [sources (vec (take-while #(not (keyword? %)) args))]
    `(let [raw-session# (r/mk-session ~@args)]
       (w/to-watched ~name raw-session# ~sources))))

(defn cancel-watch!
  "Cancel the given watched session."
  [watched-session]
  (w/unregister! (w/session-id watched-session)))

(defn clear-watches!
  "Clear all watched sessions."
  []
  (w/clear!))

(defn browse!
  "Opens a browser window to inspect the sessions being watched. It will start a
  server to host the UI if it has not already been started."
  []
  (s/start-server!)
  (b/browse-url "http:/localhost:8080"))

(defn shutdown!
  "Shuts down all watches and stops the server."
  []
  (w/clear!)
  (s/stop-server!))
