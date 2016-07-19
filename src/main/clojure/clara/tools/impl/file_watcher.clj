(ns clara.tools.impl.file-watcher
  "Utility functions for working with Clara."
  (require [clara.rules :refer :all]))

(defn watch-files
  "Watches a sequence of file paths and invokes the given function if they change.

   Returns a future that runs indefinitely but can be stopped with future-cancel."
  [path-strings watch-fn]
  (let [ws (.. (java.nio.file.FileSystems/getDefault) (newWatchService))

        paths (into #{}
                    (for [path-string path-strings]
                      (java.nio.file.Paths/get path-string
                                               (make-array String 0))))

        dirs (into #{}
                   (for [^java.nio.file.Path path paths]
                     (.getParent path)))

        watchkeys (doall
                   (for [dir dirs]
                     ;; Sun-specific sensitivity handler is needed here for reasonable
                     ;; latenency.
                     (.register dir
                                ws
                                (into-array java.nio.file.WatchEvent$Kind
                                            [java.nio.file.StandardWatchEventKinds/ENTRY_MODIFY])
                                (into-array com.sun.nio.file.SensitivityWatchEventModifier
                                            [com.sun.nio.file.SensitivityWatchEventModifier/HIGH]))))]
    (future
      (loop [cancelled false]
        (when-not cancelled
          (recur
           (try

             (Thread/sleep 20)
             (doseq [^java.nio.file.WatchKey watchkey watchkeys
                     update (.pollEvents watchkey)

                     ;; Qualify the discovered update with
                     ;; the watched directory to ensure the changed
                     ;; file is one we are watching.
                     :let [updated-path (.resolve (.watchable watchkey )
                                                  (.context update))]]
               (when (paths updated-path)
                 (try
                   (watch-fn (.toString updated-path))
                   (catch Exception e
                     (println "Exception:" e)))))

             false

             ;; The thread has been cancelled, so clean up the
             ;; watchers and return.
             (catch java.util.concurrent.CancellationException e
               (doseq [^java.nio.file.WatchKey watchkey watchkeys]
                 (.cancel watchkey))
               true))))))))



(defn watch-rules-ns
  "Watches a rule namespace for changes and displays the resuls as rules are edited."
  [{:keys [fact-fn mk-session-fn on-update-fn namespaces]}]
  {:pre [(some? fact-fn) (some? mk-session-fn) (some? on-update-fn) (some? namespaces)]}
  (let [files (into #{}
                    (for [namespace namespaces
                          v (-> (find-ns namespace)
                                (ns-publics)
                                (vals)) ]
                      (:file (meta v))))

        update-fn (fn [updated-file]

                   ;; Clear any cached sessions so we can reload them.
                   (clara.rules.compiler/clear-session-cache!)

                   ;; Reload namespaces as we detect changes.
                   (doseq [namespace namespaces]
                     (require [namespace :reload true]))
                   (-> (mk-session-fn)
                       (insert-all (fact-fn))
                       (fire-rules)
                       (on-update-fn)))]

    ;; Do an initial run.
    (update-fn (first files))

    (watch-files files update-fn)))
