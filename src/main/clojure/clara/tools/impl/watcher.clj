(ns clara.tools.impl.watcher
  (:require [clara.rules.engine :as eng]
            [clara.rules.memory :as mem]
            [clara.rules :as r]
            [clara.rules.listener :as l]
            [clara.tools.inspect :as inspect]
            [clara.tools.queries :as q]
            [clojure.pprint :as pprint]
            [clara.tools.impl.file-watcher :as fw]
            [schema.core :as s]))

(def sessions (atom {}))

(defn cancel-session-watch
  "Function to remove the watch for the given session."
  [query]
  (remove-watch sessions query))

(defn watch-sessions
  "Adds a watch to the session and returns a function
   that will cancel the watch when invoked."
  [key handler]
  (add-watch sessions
             key
             (fn [key sessions old new]
               (println "Watching for " key)
               (handler new)))
  cancel-session-watch)

(defn- update!
  "Updates the state of a registered session."
  [session-id session]
  (swap! sessions (fn [sessions] (assoc-in sessions [session-id :session] session)) ))


;; Function that will cancel the given query when called.

;; Query support.
(defmethod q/run-query :sessions
  [query key channel]
  (letfn [(session-map [sessions]
            (q/send-response! channel
                              key
                              (into {}
                                    (for [[id {name :name}] sessions]
                                      [id name]))))]

    ;; Run the query and then watch the sessions for changes.
    (session-map @sessions)
    (watch-sessions key session-map)))

(s/defn get-queries :- q/session-queries-response
  [session]
  (let [{:keys [memory rulebase]} (eng/components session)
        {:keys [productions production-nodes query-nodes]} rulebase
        queries (distinct
                 (for [[query-name query-node] query-nodes]
                   (select-keys (:query query-node) [:name :doc :params])))]
    queries))

(defmethod q/run-query :queries
  [query key channel]
  (let [[_ session-id]  query
        query-sessions (fn [sessions]
                         (if-let [session (get-in sessions [session-id :session])]
                           (q/send-response! channel
                                             key
                                             (get-queries session))
                           (q/send-failure! channel query {:type :unknown-session})))]

    (query-sessions @sessions)
    (watch-sessions key query-sessions)))


(defn clean-and-filter
  "Clears type tags from the given structure
  that may not be readable by the cient. The optional search parameter can be used to filter results."
  [data filter]
  (let [filter-strings (if (empty? filter)
                         nil
                         (remove empty? (clojure.string/split filter #" ")))]
    (into []
          (for [datum data
                :let [datum-string (with-out-str (pprint/pprint datum))]
                :when (or (empty? filter-strings)
                          (every? #(.contains ^String datum-string %) filter-strings))]

            (read-string datum-string)))))

(defmethod q/run-query :query
  [query key channel]
  (let [[_ session-id query-name params {:keys [filter]}] query
        param-seq (for [kv params
                        item kv]
                    item)

        query-sessions (fn [sessions]
                         (if-let [session (get-in sessions [session-id :session])]

                           (let [results (apply r/query session query-name param-seq)]
                             (q/send-response! channel
                                               key
                                               (clean-and-filter results filter)))

                           (q/send-failure! channel query {:type :unknown-session})))]

    (query-sessions @sessions)
    (watch-sessions key query-sessions)))

(declare to-watch-listener)

(deftype PersistentWatchListener [facts]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-watch-listener listener)))

(deftype WatchListener [facts]
  l/ITransientEventListener
  (left-activate! [listener node tokens])

  (left-retract! [listener node tokens])

  (right-activate! [listener node elements])

  (right-retract! [listener node elements])

  (insert-facts! [listener new-facts]
    (swap! facts concat new-facts))

  (insert-facts-logical! [listener node token new-facts]
    (swap! facts concat new-facts))

  (retract-facts! [listener retracted-facts]
    (let [[removed updated-facts] (mem/remove-first-of-each retracted-facts @facts)]
      (reset! facts updated-facts)))

  (retract-facts-logical! [listener node token retracted-facts]
    (let [[removed updated-facts] (mem/remove-first-of-each retracted-facts @facts)]
      (reset! facts updated-facts)))

  (add-accum-reduced! [listener node join-bindings result fact-bindings])

  (add-activations! [listener node activations])

  (remove-activations! [listener node activations])

  (fire-rules! [listener node])

  (to-persistent! [listener]
    (PersistentWatchListener. @facts)))

(defn- to-watch-listener [^PersistentWatchListener listener]
  (WatchListener. (atom (.-facts listener))))

(defn- add-watch-listener
  "Adds the listener to watch the underlying session changes."
  [session]
  (let [{:keys [listeners] :as components} (eng/components session)]
    (eng/assemble (assoc components
                         :listeners
                         (conj listeners (PersistentWatchListener. []))))))

(declare watched-session)

(defprotocol IWatchedSession
  (session-id [session])
  (sources [session])
  (facts [session])
  (raw-session [session])
  (reload-rules! [session])
  (close [session]))

(deftype WatchedSession [session-id ; Unique identifier for the session.
                         ^:volatile-mutable delegate ; Underlying session to delegate to. Mutable to support reloading rules.
                         change-log ; A sequence of updates to the session in the form of insert, retract, fire-rules operations.
                         sources ; Sources used to create the session
                         session-load-fn] ; Function to re-load the session.

  IWatchedSession
  (session-id [session] session-id)

  (sources [session] sources)

  (facts [session]
    (if-let [watch-listener (->> (eng/components delegate)
                                 :listeners
                                 (filter #(instance? PersistentWatchListener %) )
                                 (first))]
      (.-facts ^PersistentWatchListener watch-listener)
      (throw (IllegalStateException. "Watched session did not have a watch listener."))))

  (raw-session [session] delegate)

  (reload-rules! [session]
    ;; Apply the change log to the newly loaded session.
    (let [raw-session-with-facts
          (loop [[change & rest] change-log
                 applied-session (add-watch-listener (session-load-fn))]

            (if change

              (case (:type change)

                :insert
                (recur rest (eng/insert applied-session (:facts change)))

                :retract
                (recur rest (eng/retract applied-session (:facts change)))

                :fire-rules
                (recur rest (eng/fire-rules applied-session)))

              applied-session))]

      ;; Replace the delegate session with the reloaded version.
      (set! delegate raw-session-with-facts)

      ;; Update the session map so watchers pick up the change.
      (swap! sessions assoc-in [session-id :session] session)

      session))

  (close [session]
    (let [reload-future (get-in @sessions [session-id :reload-future])]
      (when reload-future
        (future-cancel reload-future))
      (swap! sessions dissoc session-id)))

  eng/ISession
  (insert [session facts]
    (watched-session
     session-id
     (eng/insert delegate facts)
     (conj change-log {:type :insert :facts facts})
     sources
     session-load-fn))

  ;; Retracts a fact.
  (retract [session fact]
    (watched-session
     session-id
     (eng/retract delegate fact)
     (conj change-log {:type :retract :facts facts})
     sources
     session-load-fn))

  ;; Fires pending rules and returns a new session where they are in a fired state.
  (fire-rules [session]
    (watched-session
     session-id
     (eng/fire-rules delegate)
     (conj change-log {:type :fire-rules})
     sources
     session-load-fn))

  ;; Runs a query agains thte session.
  (query [session query params]
    (eng/query delegate query params))

  ;; Returns the components of a session as defined in the session-components-schema
  (components [session]
    (eng/components delegate)))

(defn- watched-session
  [session-id raw-session change-log sources session-load-fn]
  {:pre [(satisfies? eng/ISession raw-session) (vector? change-log)]}
  (let [new-session (WatchedSession. session-id raw-session change-log sources session-load-fn)]
    (update! session-id new-session)
    new-session))

(defn- mk-source-watch-future
  "Returns a fture that reloads"
  [session-id sources]
  (let [source-files (into #{}
                           (for [source sources
                                 :when (symbol? source)
                                 v (-> (find-ns source)
                                       (ns-publics)
                                       (vals))

                                 :when (:file (meta v))

                                 :let [file-name (:file (meta v))
                                       qualified-name (if (.startsWith file-name "/")
                                                        file-name
                                                        (str (System/getProperty "user.dir")
                                                             "/"
                                                             file-name))]
                                 ;; Check if source is a watchable file
                                 ;; rather than a JAR resource, for instance.
                                 :when (.exists (java.io.File. qualified-name))]
                             qualified-name))]

    (fw/watch-files source-files
                    (fn [updated-file]
                      (load-file updated-file)
                      (when-let [session (get-in @sessions [session-id :session])]
                        (reload-rules! session))))))

(defn to-watched
  "Creates a watched session from the given raw session."
  [session-name ; Session name for display purposes
   session-load-fn ; Function used to load the session.
   sources] ; Rule sources for logic inspection.
  (let [raw-session (session-load-fn)
        session-id  (.toString (java.util.UUID/randomUUID))
        source-watch-future (mk-source-watch-future session-id sources)
        raw-with-listener (add-watch-listener raw-session)
        watched-session (watched-session session-id raw-with-listener [] sources session-load-fn)]

    (swap! sessions assoc session-id {:name session-name
                                      :session watched-session
                                      :reload-future source-watch-future})

    watched-session))

(defn clear!
  "Remove all outstanding watches."
  []

  (doseq [watch-key (keys (.getWatches sessions))]
    (remove-watch sessions watch-key))

  (doseq [session (vals @sessions)]
    (.close (:session session))))
