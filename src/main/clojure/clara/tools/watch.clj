(ns clara.tools.watch
  "This namespace supports watching Clara sessions and updates queries as session changes."
  (:require [clara.tools.impl.watcher :as w]
            [clara.tools.impl.server :as s]
            [clara.rules.compiler :as com]
            [clojure.java.browse :as b]))

(defn mk-watched-session
  "Creates a watch Clara session. The first argument is a name to be dispayed to the client, and the following
   arguments are simply passed to clara.rules/mk-session.
   It supports some additional arguments over mk-session. These are:

   * :write-handlers Optional custom Transit write handlers so arbitrary structures can be displayed in the browser.
     The value should be a map of types to handlers created with (transit/write-handler ...)
   * :watch-files Optional files to be watched so the session is reloaded when they are changed. By default
      the files associated with namespaces are watched."

  [name & sources-and-options]
  {:pre [(string? name)]}
  (let [sources (vec (take-while #(not (keyword? %)) sources-and-options))
        options (drop (count sources) sources-and-options)
        opt-map (apply hash-map options)]
    (w/to-watched name
                  (fn [] (com/mk-session sources-and-options))
                  sources
                  (or (:write-handlers opt-map)
                      {}))))

(defn cancel-watch!
  "Cancel the given watched session."
  [watched-session]
  (.close watched-session))

(defn clear-watches!
  "Clear all watched sessions."
  []
  (w/clear!))

(defn browse!
  "Opens a browser window to inspect the sessions being watched. It will start a
  server to host the UI if it has not already been started."
  ([]
   (browse! s/server-defaults))
  ([server-opts]
   (s/start-server! server-opts)
   (b/browse-url (str "http:/localhost:"  (:port server-opts)))))

(defn shutdown!
  "Shuts down all watches and stops the server."
  []
  (w/clear!)
  (s/stop-server!))
