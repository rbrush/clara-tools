(ns clara.tools.queries
  "Support for querying Clara sessions and rulesets."
  (:require [schema.core :as s]))

(defmulti run-query (fn [query key channel] (first query)))

(defprotocol QueryResponseChannel
  (send-response! [channel key response write-handlers])
  (send-failure! [channel key failure write-handlers]))

(defn is
  "Shorthand for Prismatic (s/one (s/eq value) name) to define schema conditions."
  [value]
  (s/one (s/eq value) (str value)))

;; Lists sessions available for inspection.
(def sessions [:sessions])
(def sessions-response [{:id s/Str
                         :name s/Str}])

;; Given a session ID, returns the queries for that session.
(def session-queries [(is :queries) (s/one s/Str "session-id")])
(def session-queries-response [{:name s/Str
                                (s/optional-key :doc) s/Str
                                :params [s/Keyword]}])


(defn classes-to-symbols
  "Replaces instances of classes with symbols"
  [form]
  (clojure.walk/postwalk (fn [value]
                           (if (instance? Class value)
                             (symbol (.getName ^Class value))
                             value))
                         form))
