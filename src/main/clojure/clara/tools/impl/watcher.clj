(ns clara.tools.impl.watcher
  (:require [clara.rules.engine :as eng]
            [clara.rules.memory :as mem]
            [clara.rules :as r]
            [clara.rules.listener :as l]
            [clara.tools.inspect :as inspect]
            [clara.tools.queries :as q]
            [clojure.pprint :as pprint]
            [clojure.string]
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
             (fn [_key _sessions _old new]
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
                                      [id name]))
                              {}))]

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
                                             (get-queries session)
                                             (get-in sessions [session-id :write-handlers]))
                           (q/send-failure! channel key {:type :unknown-session} {})))]

    (query-sessions @sessions)
    (watch-sessions key query-sessions)))


(defn clean-and-filter
  "Filters items that do not contain anything in the given filter."
  [data filter]
  (let [filter-strings (if (empty? filter)
                         nil
                         (for [filter-string (clojure.string/split filter #" ")
                               :when (not (empty? filter-string))]
                           (.toLowerCase ^String filter-string)))]
    (into []
          (for [datum data
                :let [datum-string (with-out-str (pprint/pprint datum))
                      lower-string (.toLowerCase ^String datum-string)]
                :when (or (empty? filter-strings)
                          (every? #(.contains ^String lower-string %) filter-strings))]

            datum))))

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
                                               (clean-and-filter results filter)
                                               (get-in sessions [session-id :write-handlers])))

                           (q/send-failure! channel key {:type :unknown-session} {})))]

    (query-sessions @sessions)
    (watch-sessions key query-sessions)))

(declare to-watch-listener)

(deftype PersistentWatchListener [facts]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-watch-listener listener)))

(defn- add-facts [facts new-facts]
  (into facts new-facts))

(defn- remove-facts [facts retracted-facts]
  (let [[_removed remaining] (mem/remove-first-of-each retracted-facts facts)]
    (vec remaining)))

(deftype WatchListener [facts]
  l/ITransientEventListener
  (left-activate! [listener node tokens])
  (left-retract! [listener node tokens])
  (right-activate! [listener node elements])
  (right-retract! [listener node elements])

  (insert-facts! [listener node token new-facts]
    (swap! facts add-facts new-facts))

  (alpha-activate! [listener node facts])

  (insert-facts-logical! [listener node token new-facts]
    (swap! facts add-facts new-facts))

  (retract-facts! [listener node token retracted-facts]
    (swap! facts remove-facts retracted-facts))

  (alpha-retract! [listener node facts])

  (retract-facts-logical! [listener node token retracted-facts]
    (swap! facts remove-facts retracted-facts))

  (add-accum-reduced! [listener node join-bindings result fact-bindings])
  (remove-accum-reduced! [listener node join-bindings fact-bindings])
  (add-activations! [listener node activations])
  (remove-activations! [listener node activations])
  (fire-activation! [listener activation resulting-operations])
  (fire-rules! [listener node])
  (activation-group-transition! [listener original-group new-group])

  (to-persistent! [listener]
    (PersistentWatchListener. @facts)))

(defn- to-watch-listener [^PersistentWatchListener listener]
  (WatchListener. (atom (.-facts listener))))

(defn- watch-listener? [listener]
  (instance? PersistentWatchListener listener))

(defn- add-watch-listener
  "Adds the listener to watch the underlying session changes."
  [session]
  (eng/with-listener session (PersistentWatchListener. [])))

(defn- pending-changes
  "Returns the changes in the change log made since rules were last fired."
  [change-log]
  (reverse (take-while #(not= :fire-rules (:type %)) (rseq change-log))))

(defn- replay-change
  "Applies an insert or retract from the change log to a raw session."
  [session {:keys [type facts]}]
  (case type
    :insert (eng/insert session facts)
    :retract (eng/retract session facts)))

(defn- track-external-changes
  "Clara does not notify listeners of external insertions and retractions when
   firing rules with the :cancelling option, so apply them to the watched facts here.
   Insertions are applied before retractions, matching the engine.

   Replacing the listener drops the session's pending operations, so the changes
   are replayed on the returned session."
  [session changes]
  (let [facts-of (fn [type] (mapcat :facts (filter #(= type (:type %)) changes)))
        listener (first (eng/find-listeners session watch-listener?))
        facts (-> (.-facts ^PersistentWatchListener listener)
                  (add-facts (facts-of :insert))
                  (remove-facts (facts-of :retract)))]
    (reduce replay-change
            (-> session
                (eng/remove-listeners watch-listener?)
                (eng/with-listener (PersistentWatchListener. facts)))
            changes)))

(defn- fire-rules-tracked
  "Fires rules on a raw session, keeping the watched facts in sync with the
   given changes made since rules were last fired."
  [raw-session opts pending]
  ;; Track external changes before firing, like the engine applies them,
  ;; so retractions made by rules see them.
  (eng/fire-rules (cond-> raw-session
                    (:cancelling opts) (track-external-changes pending))
                  opts))

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
    (if-let [watch-listener (first (eng/find-listeners delegate watch-listener?))]
      (.-facts ^PersistentWatchListener watch-listener)
      (throw (IllegalStateException. "Watched session did not have a watch listener."))))

  (raw-session [session] delegate)

  (reload-rules! [session]
    ;; Apply the change log to the newly loaded session.
    (let [raw-session-with-facts
          (loop [[change & rest] change-log
                 applied-session (add-watch-listener (session-load-fn))
                 pending []]

            (if change

              (case (:type change)

                :insert
                (recur rest (eng/insert applied-session (:facts change)) (conj pending change))

                :retract
                (recur rest (eng/retract applied-session (:facts change)) (conj pending change))

                :fire-rules
                (recur rest (fire-rules-tracked applied-session (:opts change) pending) []))

              applied-session))]

      ;; Replace the delegate session with the reloaded version.
      (set! delegate raw-session-with-facts)

      ;; Update the session map so watchers pick up the change.
      (swap! sessions assoc-in [session-id :session] session)

      session))

  (close [session]
    (when-let [source-watcher (get-in @sessions [session-id :source-watcher])]
      (fw/stop! source-watcher))
    (swap! sessions dissoc session-id))

  eng/ISession
  (insert [session facts]
    (watched-session
     session-id
     (eng/insert delegate facts)
     (conj change-log {:type :insert :facts facts})
     sources
     session-load-fn))

  ;; Retracts facts.
  (retract [session facts]
    (watched-session
     session-id
     (eng/retract delegate facts)
     (conj change-log {:type :retract :facts facts})
     sources
     session-load-fn))

  ;; Fires pending rules and returns a new session where they are in a fired state.
  (fire-rules [session]
    (eng/fire-rules session {}))

  (fire-rules [session opts]
    (watched-session
     session-id
     (fire-rules-tracked delegate opts (pending-changes change-log))
     (conj change-log {:type :fire-rules :opts opts})
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

(defn- mk-source-watcher
  "Returns a file watcher that reloads the session when its rule sources change."
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
   sources  ; Rule sources for logic inspection.
   write-handlers]
  (let [raw-session (session-load-fn)
        session-id  (.toString (java.util.UUID/randomUUID))
        source-watcher (mk-source-watcher session-id sources)
        raw-with-listener (add-watch-listener raw-session)
        watched-session (watched-session session-id raw-with-listener [] sources session-load-fn)]

    (swap! sessions assoc session-id {:name session-name
                                      :session watched-session
                                      :source-watcher source-watcher
                                      :write-handlers write-handlers})

    watched-session))

(defn clear!
  "Remove all outstanding watches."
  []

  (doseq [watch-key (keys (.getWatches ^clojure.lang.IRef sessions))]
    (remove-watch sessions watch-key))

  (doseq [session (vals @sessions)]
    (.close (:session session))))
