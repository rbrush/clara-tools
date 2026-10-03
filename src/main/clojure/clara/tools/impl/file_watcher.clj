(ns clara.tools.impl.file-watcher
  "Utility functions for working with Clara."
  (:require [clara.rules :refer [fire-rules insert-all]]
            [clara.rules.compiler :as com])
  (:import [java.nio.file ClosedWatchServiceException FileSystems Path Paths
            StandardWatchEventKinds WatchEvent WatchEvent$Kind WatchKey WatchService]))

(defn- normalize ^Path [path]
  (-> (Paths/get (str path) (make-array String 0))
      (.toAbsolutePath)
      (.normalize)))

(defn watch-files
  "Watches a sequence of file paths and invokes the given function with the
   path string of a file when it changes. Only the files' own directories are
   watched, not their subdirectories.

   Returns a watcher that can be stopped with stop!, or nil if there are no files to watch."
  [path-strings watch-fn]
  (let [paths (into #{} (map normalize) path-strings)
        dirs (into #{} (map #(.getParent ^Path %)) paths)]
    (when (seq dirs)
      (let [ws (.newWatchService (FileSystems/getDefault))]
        (try
          (doseq [^Path dir dirs]
            (.register dir ws ^"[Ljava.nio.file.WatchEvent$Kind;"
                       (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                                                    StandardWatchEventKinds/ENTRY_MODIFY])))
          (catch Throwable t
            (.close ws)
            (throw t)))
        (doto (Thread.
               (fn []
                 (try
                   (loop []
                     (let [^WatchKey watchkey (.take ws)
                           ;; A single save often produces several events (create and
                           ;; modify), so reload each changed file only once per batch.
                           updated-paths (into #{}
                                               (comp (map #(.context ^WatchEvent %))
                                                     (filter #(instance? Path %))
                                                     ;; Qualify the changed file with the watched directory
                                                     ;; to ensure it is one we are watching.
                                                     (map #(.normalize (.resolve ^Path (.watchable watchkey) ^Path %)))
                                                     (filter paths))
                                               (.pollEvents watchkey))]
                       (doseq [updated-path updated-paths]
                         (try
                           (watch-fn (str updated-path))
                           ;; Keep watching even if reloading the file fails.
                           (catch Throwable t
                             (.printStackTrace t))))
                       (.reset watchkey)
                       (recur)))
                   (catch ClosedWatchServiceException _)
                   (catch InterruptedException _)))
               "clara-tools-file-watcher")
          (.setDaemon true)
          (.start))
        ws))))

(defn stop!
  "Stops a watcher returned by watch-files."
  [watcher]
  (when watcher
    (.close ^WatchService watcher)))

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
                   (com/clear-session-cache!)

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
