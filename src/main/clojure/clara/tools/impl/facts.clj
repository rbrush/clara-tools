(ns clara.tools.impl.facts
  "Queries for the facts within a session."
  (:require [clara.tools.inspect :as inspect]
            [clara.tools.queries :as q]
            [clara.tools.impl.watcher :as w]))


(defn- fact-to-id [fact]
  (str (.getName (type fact)) "-" (hash fact)))

(defn- fact-to-id-map
  "Takes all facts in the given session inspection and produces a map of id-to-fact"
  [inspection]
  (let [facts (-> inspection :condition-matches vals flatten distinct)]
       (into {}
             (for [fact facts]
               [fact (fact-to-id fact)]))))

(defn- cond-to-id [condition]
  (str "COND-" (hash condition)))

(defn- explanation-to-graph
  "Converts an explanation to a graph."
  [fact fact-id explanation fact-to-id]
  (let [conditions (map (fn [[fact condition]] condition ) (:matches explanation))]
    (apply merge-with conj
           {:nodes  {fact-id {:type :fact
                              :value (pr-str fact)}}

            ;; Link last condition to the target fact.
            :edges (into (if (empty? conditions)
                           {}
                           {[(cond-to-id (last conditions)) fact-id] {:type :asserts}})

                         ;; Create edges between successive conditions.
                         (map (fn [cond next-cond]
                                [[(cond-to-id cond) (cond-to-id next-cond)] {:type :and}])
                              conditions
                              (rest conditions)))}

           ;; Create fact and conditions nodes and edges between them.
           (for [[fact condition] (:matches explanation)]

             ;; Accumulated values will not have a fact id, so
             ;; generate one with a hash so it will be connected into the graph.
             {:nodes {(fact-to-id fact (str (hash fact))) {:type :fact
                                                           :value (pr-str fact)}

                      ;; Simply include the accumulator node in the display.
                      (cond-to-id condition) (if (fact-to-id fact)
                                               {:type :condition
                                                :value condition}
                                               {:type :accumulator
                                                :value condition})}

              :edges {[(fact-to-id fact (str (hash fact))) (cond-to-id condition)]
                      ;; Use an accumulated edge label for generated
                      ;; fact ids.
                      {:type (if (fact-to-id fact) :matches :accumulated)
                       :value condition} }
              } ))))

(defn- explanation-graph
  "Returns a graph that explains the support for the fact with the given ID."
  [{:keys [fact-to-id id-to-fact id-to-explanation] :as session-info} fact-id]
  (let [explanation (id-to-explanation fact-id)]
    (explanation-to-graph (id-to-fact fact-id) fact-id explanation fact-to-id)))

(defn to-session-info
  [session]
  (let [inspection (inspect/inspect session)
        fact-to-id (fact-to-id-map inspection)
        fact-to-explanation (for [[node insertions] (-> inspection :insertions)
                                  {:keys [explanation fact]} insertions]
                              [fact explanation])

        facts-by-type (group-by type (keys fact-to-id))]

    {:fact-to-id fact-to-id

     :facts-by-type facts-by-type

     :id-to-fact (into {}
                       (for [[fact id] fact-to-id]
                         [id fact]))

     :id-to-explanation (into {}
                              (for [[fact explanation] fact-to-explanation]
                                [(fact-to-id fact) explanation]))}))


;; Returns a sequence of fact types.
(defmethod q/run-query :list-fact-types
  [[_ session-id :as query] key channel]
  (letfn [(list-session-facts [sessions]
            (if-let [session (get-in sessions [session-id :session])]
              (q/send-response! channel
                                key
                                (->> (w/facts session)
                                     (map (fn [fact] (.getName (type fact)) ))
                                     (distinct)
                                     (sort)))

              (q/send-failure! channel key {:type :unknown-session})))]

    (list-session-facts @w/sessions)
    (w/watch-sessions query list-session-facts)))

;; Returns a sequence of [fact-id fact] tuples for facts of the given type.
(defmethod q/run-query :list-facts-by-type
  [[_ session-id fact-type {filter :filter} :as query] key channel]
  (letfn [(list-session-facts [sessions]
            (if-let [session (get-in sessions [session-id :session])]
              (q/send-response! channel
                                key
                                (into {}
                                      (w/clean-and-filter
                                       (for [fact (w/facts session)
                                             :when (= fact-type (.getName (type fact)))]
                                         [(fact-to-id fact) fact])
                                       filter)))

              (q/send-failure! channel key {:type :unknown-session})))]

    (list-session-facts @w/sessions)
    (w/watch-sessions query list-session-facts)))

(defmethod q/run-query :explain-fact
  [[_ session-id fact-id :as query] key channel]
  (letfn [(list-session-facts [sessions]
            (if-let [session (get-in sessions [session-id :session])]
              (q/send-response! channel
                                key
                                (explanation-graph (to-session-info session) fact-id))

              (q/send-failure! channel key {:type :unknown-session})))]

    (list-session-facts @w/sessions)
    (w/watch-sessions query list-session-facts)))
