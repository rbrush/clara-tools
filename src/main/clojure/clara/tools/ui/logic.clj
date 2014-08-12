(ns clara.tools.ui.logic
  "Resource for serving the logic graph."
  (:require [compojure.core :refer [defroutes context GET]]
            [hiccup.page :as page]
            [clojure.string :as string]
            [clara.tools.ui.common :as common]
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

(defn render-page [namespaces]
  (page/html5
   (common/with-layout
     "Clara Ruleflow"
     [:div
      [:div [:h2 (str "Logic graph for namespaces: " namespaces)]]
      [:div
       [:svg {:id "testdraw" :width "90%" :height "1000"}
        [:g {:transform "translate(20,20)"}]]]]

     ;; Display the flow!
     (str "clara.tools.ui.ruleflow_client.show_logic (\"" (codec/url-encode namespaces) "\")")
;     "clara.tools.ui.ruleflow_client.blarg()"
     )))

(def graph-data {:nodes {"A" {:label "A Node."}
                         "B" {:label "Another Node"}
                         "C" {:label "Yet another Node."}}
                 :edges {["A" "B"] {}
                         ["B" "C"] {}
                         ["A" "C"] {:label "Dance!!"}
                         }
                 })

(def graph-data2 {:nodes
                  {"P--877954782" {:label ":production"},
                   "P-1259300896" {:label ":production"}},
                  :edges {["P--877954782" "P-1259300896"] {:label "beep"}}
                  })

(comment {["1-422838530" "0-422838530"] {:label ":component-of"},
          ["1--1039983285" "0--1039983285"] {:label ":component-of"}})

;; Compojure routes to create this page.
(defroutes routes
  (GET "/" {{namespaces :namespaces} :params}
       (render-page namespaces))

  (GET "/graph" {{namespaces-str :namespaces} :params}
       ;; TODO: parse namespaces and display graph...
       (let [namespaces (mapv symbol
                              (clojure.string/split namespaces-str #" "))
             graph (logic/logic-graph namespaces)
             client-graph (logic-to-display-graph graph)]

         (clojure.pprint/pprint client-graph)
         (println "DONE!")

         {:status 200
          :headers {"Content-Type" "application/edn"}
          :body (pr-str client-graph)})))
