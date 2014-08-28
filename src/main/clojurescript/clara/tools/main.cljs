(ns clara.tools.main
  "Main entrypoint for the Clara Tools application."
  (:require [goog.events :as events]
            [clara.tools.apps.logicview :as logicview]
            [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:import goog.History
           goog.history.EventType))

(let [h (History.)]
  (goog.events/listen h "navigate" #(secretary/dispatch! (.-token %)))
  (doto h
    (.setEnabled true)))
