(ns clara.tools.examples.commands
  "Rules that retract the facts they handle."
  (:require [clara.rules :refer [defrule insert-unconditional! retract!]]))

(defrecord Command [name])

(defrecord Done [name])

(defrule handle-command
  [?command <- Command (= ?name name)]
  =>
  (retract! ?command)
  (insert-unconditional! (->Done ?name)))
