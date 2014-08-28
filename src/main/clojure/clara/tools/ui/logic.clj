(ns clara.tools.ui.logic
  "Resource for serving the logic graph."
  (:require [compojure.core :refer [defroutes context GET]]
            [clojure.string :as string]
            [clara.tools.logic-graph :as logic]
            [ring.util.codec :as codec]))

(defmulti display-node :type)

(defmethod display-node :fact
  [node]
  {:label (last (string/split (str (:value node)) #"\." ))  })


(defmethod display-node :fact-condition
  [node]
  {:label (pr-str (get-in node [:value :constraints]))})

(defmethod display-node :production
  [node]
  {:label (pr-str (get-in node [:value :doc]))})

(defmethod display-node :and
  [node]
  {:label "ALL OF"})

(defmethod display-node :or
  [node]
  {:label "ANY OF"})

(defmethod display-node :default
  [node]
  {:label (str (:type node))})

(defn logic-to-display-graph
  "Converts a graph of rule logic to a displayable form."
  [{:keys [nodes edges] :as logic-graph}]
  {:nodes (into {}
                (for [[node-id node]  nodes]
                  [node-id (display-node node)]))
   :edges (into {}
                (for [[edge-tuple edge] edges]
                  [edge-tuple {}]))})

;; Compojure routes to create this page.
(defroutes routes

  (GET "/graph" {{namespaces-str :namespaces} :params}

       (let [namespaces (mapv symbol
                              (clojure.string/split namespaces-str #" "))
             graph (logic/logic-graph namespaces)]

         {:status 200
          :headers {"Content-Type" "application/edn"}
          :body (pr-str graph)})))
