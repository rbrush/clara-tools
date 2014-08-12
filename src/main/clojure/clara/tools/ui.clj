(ns clara.tools.ui
  "Visual tool support for using Clara."
  (:require [clara.tools.ui.server :as server]
            [clojure.java.browse :as browse]
            [ring.util.codec :as codec]))


(defn show-logic-graph
  "Opens a browser window to show a graph of the rule logic."
  [& namespaces]
  {:pre [(every? symbol? namespaces)]}
  ;; Ensure the UI server is running.
  (server/start-server!)

  ;; TODO: add namespaces as params...
  (browse/browse-url (str "http://localhost:8080/logic?namespaces="
                          (->> namespaces
                               (clojure.string/join "+")
                               (codec/url-encode)))))
