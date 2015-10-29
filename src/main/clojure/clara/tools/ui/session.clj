(ns clara.tools.ui.session
  (:require [clara.rules.engine :as eng]
            [compojure.core :refer [defroutes context GET]]
            [clara.tools.inspect :as inspect]
            [clojure.set :as set]
            [schema.core :as s]))


(def SessionSchema
  {:id-to-fact {s/Str s/Any}
   :id-to-explanation {s/Str s/Any}})


(defn- fact-to-id-map
  "Takes all facts in the given session inspection and produces a map of id-to-fact"
  [inspection]
  (let [facts (-> inspection :condition-matches vals flatten distinct)]
       (into {}
             (for [fact facts]
               [fact (str (.getName (type fact)) "-" (hash fact))]))))

(def registered-sessions (atom {}) )

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

(defn to-session-info
  [session]
  (let [inspection (inspect/inspect session)
        fact-to-id (fact-to-id-map inspection)
        fact-to-explanation (for [[node insertions] (-> inspection :insertions)
                                  {:keys [explanation fact]} insertions]
                              [fact explanation])]

    {:fact-to-id fact-to-id

     :id-to-fact (into {}
                       (for [[fact id] fact-to-id]
                         [id (pr-str fact)]))

     :id-to-explanation (into {}
                              (for [[fact explanation] fact-to-explanation]
                                [(fact-to-id fact) explanation]))}))

(defn explanation-graph
  "Returns a graph that explains the support for the fact with the given ID."
  [{:keys [fact-to-id id-to-fact id-to-explanation] :as session-info} fact-id]
  (let [explanation (id-to-explanation fact-id)]
    (explanation-to-graph (id-to-fact fact-id) fact-id explanation fact-to-id)))


(defn register!
  "Registers a session to be visible at the service."
  [session]

  (let [session-id (name (gensym "S"))]
    (swap! registered-sessions assoc session-id (to-session-info session))
    session-id))


(defroutes routes

  (GET "/:id/facts" [id]
       (if-let [session-info (get @registered-sessions id)]
         {:status 200
          :headers {"Content-Type" "application/edn"}
          :body (pr-str (:id-to-fact session-info))}

         {:status 404
          :headers {"Content-Type" "text/plain"}
          :body (str "Unable to resolve session " id)}))

  (GET "/:id/fact/:fact-id/explanation" [id fact-id]

       (if-let [session-info (get @registered-sessions id)]

         {:status 200
          :headers {"Content-Type" "application/edn"}
          :body (pr-str (explanation-graph session-info fact-id))}

         {:status 404
          :headers {"Content-Type" "text/plain"}
          :body (str "Unable to resolve session " id)})))


;; (-> (sess/to-session-info session) (sess/explanation-graph "strangeloop.demo.RecommendedMeds--617884174" ) pprint)
