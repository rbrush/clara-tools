(ns clara.tools.apps.logicview.graph
  "Display and manipulate the graph for viewing logic."
  (:require [clojure.string :as str]
            [reagent.core :as reagent :refer [atom]]
            [clara.tools.graphing :as graphing]
            [clara.tools.dagre :as dagre]))


;; The fact types for which there is focus.
(def focused-facts  (atom #{}))

;; The logic graph itself.
(def logic-graph (atom {:nodes []
                        :edges []}))

(defmulti display-node :type)

(defmethod display-node :fact
  [node]
  {:label (last (clojure.string/split (str (:value node)) #"\." ))  })


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

(defmethod display-node :not
  [node]
  {:label "NOT"})

(defmethod display-node :default
  [node]
  {:label (str (:type node))})

(defn- logic-to-display-graph
  "Converts a graph of rule logic to a displayable form."
  [{:keys [nodes edges] :as logic-graph}]
  {:nodes (into {}
                (for [[node-id node]  nodes]
                  [node-id (display-node node)]))
   :edges (into {}
                (for [[edge-tuple edge] edges]
                  [edge-tuple {}]))})


(defn- render-logic-graph-setup []

  ;; We explicitly dereference the logic graph content to trigger a re-render,
  ;; since all further access is did in the did-update function.
  (deref logic-graph)
  (deref focused-facts)
  [:div
   [:div { ; :style {:height "100%" :width "100%"}
          :react-key "d3-node" ;; ensure React knows this is non-reusable
          :ref "d3-node"       ;; label it so we can retrieve it via get-node
          :id "d3-node"}
    [:svg {:width "100%" :height 800
           :style {:outline "thin solid #C0C0C0"}}

     [:g {:transform "translate(20,20)"}]]]])

(defn render-logic-graph [on-context-menu]

  (let [do-render! #(let [filtered-graph (if (> (count @focused-facts) 0)
                                           (graphing/filter-facts @logic-graph
                                                                  (re-pattern (str/join "|" (map :name @focused-facts))))
                                           @logic-graph)
                          display-graph (logic-to-display-graph filtered-graph)
                          dagre-graph (dagre/mk-graph "#d3-node svg g"
                                                      display-graph
                                                      {:on-node-click nil
                                                       :on-context-menu on-context-menu})]
                      (dagre/render! dagre-graph))]

    (reagent/create-class
     {:render render-logic-graph-setup
      :component-did-mount do-render!
      :component-did-update do-render!})))
