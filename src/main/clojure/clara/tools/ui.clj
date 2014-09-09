(ns clara.tools.ui
  "Visual tool support for using Clara."
  (:require [clara.tools.ui.server :as server]
            [clojure.java.browse :as browse]
            [clara.tools.ui.session :as session]
            [ring.util.codec :as codec]))


(defn show-logic-graph
  "Opens a browser window to show a graph of the rule logic."
  ([namespaces]
     {:pre [(every? symbol? namespaces)]}
     ;; Ensure the UI server is running.
     (server/start-server!)

     (browse/browse-url (str "http://localhost:8080/#/logic?namespaces="
                             (->> namespaces
                                  (clojure.string/join "+")
                                  (codec/url-encode))))))

(defn show-session
  "Opens a browser window to inspect the content of the given session."
  [session]
  (server/start-server!)
  (let [session-id (session/register! session)]
    (browse/browse-url (str "http://localhost:8080/#/session?clara-session=" session-id))))
