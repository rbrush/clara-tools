(ns clara.tools.client.sessions.fact-view
  "Tab to explore Clara facts."
  (:require [reagent.core :as reagent :refer [atom]]
            [clara.tools.client.bootstrap :as bs]
            [clara.tools.client.record-table :as rt]
            [clara.tools.client.channel :as chan]
            [clara.tools.client.graphs.dagre :as dagre]))


(defn show-fact-types [fact-types on-type-change]
  [bs/table {:striped true :bordered true}
   [:thead
    [:tr
     [:th "Type"]]
    (into [:tbody]
          (for [fact-type @fact-types]
            [:tr
             [:td {:onClick (fn [] (on-type-change fact-type))} fact-type]]))]])


(def empty-graph {:nodes {} :edges {}})

(defmulti display-node :type)

(defmethod display-node :condition
  [node]
  {:label (str (get-in node [:value :constraints]))})

(defmethod display-node :accumulator
  [node]
  (let [condition (:value node)
        {accum :accumulator {constraints :constraints fact-type :type} :from} condition]
    {:label (str "Accumulate " accum " from " fact-type " when " constraints)}))

(defmethod display-node :default
  [node]
  {:label (str (:value node))})

(defn- session-to-display-graph
  "Converts a graph of session data to a displayable form."
  [{:keys [nodes edges] :as session-graph}]
  {:nodes (into {}
                (for [[node-id node]  nodes]
                  [node-id (display-node node)]))
   :edges (into {}
                (for [[edge-tuple edge] edges]
                  [edge-tuple {:label (name (:type edge))}]))})

;; Graph containing the explanation to visualize.
;; TODO: investigate making this part of component state.
(def explanation-graph (atom empty-graph))

(defn- explanation-info-setup []

  ;; Reference the graph here so we know to re-render the
  ;; component when the graph changes.
  (if (= empty-graph @explanation-graph)
    [:div "Fact was inserted directly so no explanation available."]

    [:div { ; :style {:height "100%" :width "100%"}
           :react-key "d3-node" ;; ensure React knows this is non-reusable
           :ref "d3-node"       ;; label it so we can retrieve it via get-node
           :id "d3-node"}
     [:svg {:width "100%" :height 300
            :style {:outline "thin solid #C0C0C0"}}

      [:g {:transform "translate(20,20)"}]]]))

(defn render-explanation-graph []
  (let [do-render! (fn [comp argvs]
                     (let [display-graph (session-to-display-graph @explanation-graph)
                           dagre-graph (dagre/mk-graph "#d3-node svg g"
                                                       display-graph
                                                       {:on-node-click nil
                                                        :on-context-menu nil})]
                       (dagre/render! dagre-graph)))]

    (reagent/create-class
     {:reagent-render explanation-info-setup
      :component-did-mount do-render!
      :component-did-update do-render!})))


(defn show-explanation [fact-id explanation-graph]
  [render-explanation-graph explanation-graph])

(defn fact-view [view-state]
  (let [fact-types (atom [])
        active-type (reagent/cursor view-state [:active-type])
        fact-id (reagent/cursor view-state [:fact-id])
        fact-list (atom [])
        table-state (atom nil)
        on-type-change (fn [type]
                         (when (not= type @active-type)
                           (reset! active-type type)
                           (reset! table-state {:title type :path [] :search ""})))]

    (chan/run-query! [:list-fact-types (:active-session @view-state)]
                     [:list-fact-types (:active-session @view-state)]
                     (fn [results]
                       (reset! fact-types results)))

    (reagent.ratom/run!
     (chan/run-query! [:explain-fact (:active-session @view-state)]
                      [:explain-fact (:active-session @view-state) @fact-id]
                      (fn [results] (reset! explanation-graph results))))

    (reagent.ratom/run!
     (when @active-type
       (chan/run-query! [:list-facts-by-type (:active-session @view-state)]
                        [:list-facts-by-type
                         (:active-session @view-state)
                         @active-type
                         {:filter (:search @table-state)}]
                        (fn [results] (reset! fact-list results)))))

    (fn [view-state]

      [:div
       [:div.container-fluid
        [bs/row
         [:div.col-lg-3.col-md-3.col-sm-3
          [show-fact-types fact-types on-type-change]]

         [:div.col-lg-9.col-md-9.col-sm-9

          (when @fact-id
            [bs/panel {:header "Fact Explanation"}
             [render-explanation-graph]
             [bs/button {:onClick (fn [] (reset! fact-id nil))} "Clear" ]])

          (when @active-type
            [rt/render-table
             fact-list
             {:row-id-fn first
              :get-record-fn second
              :on-row-click-fn (fn [row-id] (reset! fact-id row-id))}
             table-state])]]]])))
