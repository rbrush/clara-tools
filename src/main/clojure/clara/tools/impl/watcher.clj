(ns clara.tools.impl.watcher
  (:require [clara.rules.engine :as eng]
            [clara.rules.memory :as mem]
            [clara.rules :as r]
            [clara.rules.listener :as l]
            [clara.tools.inspect :as inspect]
            [clara.tools.queries :as q]
            [schema.core :as s]))

(def sessions (atom {}))

(defn cancel-session-watch
  "Function to remove the watch for the given session."
  [query]
  (remove-watch sessions query))

(defn watch-sessions
  "Adds a watch to the session and returns a function
   that will cancel the watch when invoked."
  [query handler]
  (add-watch sessions
             query
             (fn [key sessions old new]
               (handler new)))
  cancel-session-watch)

(defn register!
  "Registers a session with the given name and returns a key used to update or unregister it."
  [session-name session]
  (let [session-id (.toString (java.util.UUID/randomUUID))]
    (swap! sessions assoc session-id {:name session-name
                                      :session session})
    session-id))

(defn update!
  "Updates the state of a registered session."
  [session-id session]
  (swap! sessions (fn [sessions] (assoc-in sessions [session-id :session] session)) ))

(defn unregister!
  "Unregister a session."
  [session-id]
  (swap! sessions dissoc session-id))

(defn clear!
  "Remove all outstanding watches."
  []
  ;; Remove all watches...
  (reset! sessions {}))


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
    (watch-sessions query session-map)))

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
    (watch-sessions query query-sessions)))


(defn clean-and-filter
  "Clears type tags from the given structure
  that may not be readable by the cient. The optional search parameter can be used to filter results."
  [data filter]
  (let [filter-strings (if (empty? filter)
                         nil
                         (remove empty? (clojure.string/split filter #" ")))]
    (into []
          (for [datum data
                :let [datum-string (with-out-str (clojure.pprint/pprint datum))]
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
    (watch-sessions query query-sessions)))

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
    (reset! facts (mem/remove-first-of-each retracted-facts @facts)))

  (retract-facts-logical! [listener node token retracted-facts]
    (reset! facts (mem/remove-first-of-each retracted-facts @facts)))

  (add-accum-reduced! [listener node join-bindings result fact-bindings])

  (add-activations! [listener node activations])

  (remove-activations! [listener node activations])

  (fire-rules! [listener node])

  (to-persistent! [listener]
    (PersistentWatchListener. @facts)))

(defn- to-watch-listener [^PersistentWatchListener listener]
  (WatchListener. (atom (.-facts listener))))

(declare watched-session)

(defprotocol IWatchedSession
  (session-id [session])
  (sources [session])
  (facts [session])
  (raw-session [session]))

(deftype WatchedSession [session-id delegate sources]

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

  eng/ISession
  (insert [session facts]
    (watched-session
     session-id
     (eng/insert delegate facts)
     sources))

  ;; Retracts a fact.
  (retract [session fact]
    (watched-session
     session-id
     (eng/retract delegate fact)
     sources))

  ;; Fires pending rules and returns a new session where they are in a fired state.
  (fire-rules [session]
    (watched-session
     session-id
     (eng/fire-rules delegate)
     sources))

  ;; Runs a query agains thte session.
  (query [session query params]
    (eng/query delegate query params))

  ;; Returns the components of a session as defined in the session-components-schema
  (components [session]
    (eng/components delegate)))


(defn- watched-session
  [session-id raw-session sources]
  (let [new-session (WatchedSession. session-id raw-session sources)]
    (update! session-id new-session)
    new-session))

(defn- add-watch-listener
  "Adds the listener to watch the underlying session changes."
  [session]
  (let [{:keys [listeners] :as components} (eng/components session)]
    (eng/assemble (assoc components
                         :listeners
                         (conj listeners (PersistentWatchListener. []))))))

(defn to-watched
  "Creates a watched session from the given raw session."
  [name raw-session sources]
  (let [session-id  (register! name raw-session)
        raw-with-listener (add-watch-listener raw-session)]
    (watched-session session-id raw-with-listener sources)))
