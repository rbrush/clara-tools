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
     [:th "Type"]]]
   (into [:tbody]
         (for [fact-type @fact-types]
           [:tr
            [:td {:onClick (fn [] (on-type-change fact-type))} fact-type]]))])


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

(def context-details (atom nil))

(defn context-menu-content [node-key node fact-ids-ref]
  [:div.btn-group.btn-group-vertical

   [:button.btn.btn-default
    {:type "button"
     :on-click #(js/alert (str node))}
    "Include!"]])

(defn context-menu [fact-ids]
  (when-let [{:keys [x y node-key] :as details} @context-details]
    [:div {:style {:position "absolute"
                   :left (str (- x 10) "px")
                   :top (str (- y 5) "px")
                   :display "inline-block"
                   :z-index 1000}
           :on-mouse-leave #(reset! context-details nil)}
     (let [node (get-in @explanation-graph [:nodes node-key])]

       ;; Currently we only display a menu on fact nodes.
       (when (= :fact (:type node))
         [:div.btn-group.btn-group-vertical
          (if (some #{node-key} @fact-ids)
            [:button.btn.btn-default
             {:type "button"
              :on-click #(swap! fact-ids (fn [ids] (remove #{node-key} ids)))}
             "Remove Supporting Explanation"]
            [:button.btn.btn-default
             {:type "button"
              :on-click #(swap! fact-ids conj node-key)}
             "Include Supporting Explanation"])]))]))

(defn- explanation-info-setup []

  ;; Reference the graph here so we know to re-render the
  ;; component when the graph changes.
  (if (= empty-graph @explanation-graph)
    [:div "Fact was inserted directly so no explanation available."]

    [:div {:style {:height "85%" :width "100%"}
           :react-key "d3-node" ;; ensure React knows this is non-reusable
           :ref "d3-node"       ;; label it so we can retrieve it via get-node
           :id "d3-node"}
     [:svg {:width "100%" :height "100%"
            :style {:outline "thin solid #C0C0C0"}}

      [:g {:transform "translate(20,20)"}]]]))

(defn render-explanation-graph []
  (let [do-render! (fn [comp argvs]
                     (let [display-graph (session-to-display-graph @explanation-graph)
                           dagre-graph (dagre/mk-graph "#d3-node svg g"
                                                       display-graph
                                                       {:on-node-click nil
                                                        :on-context-menu  #(reset! context-details %)})]
                       (dagre/render! dagre-graph)))]

    (reagent/create-class
     {:reagent-render explanation-info-setup
      :component-did-mount do-render!
      :component-did-update do-render!})))


(defn fact-view [view-state]
  (let [active-type (reagent/cursor view-state [:active-type])
        fact-ids (reagent/cursor view-state [:fact-ids])
        table-state (reagent/cursor view-state [:table-state])

        fact-types (atom []) ; List of fact types returned from query
        fact-list (atom []) ; List of facts returned from query

        on-type-change (fn [type]
                         (when (not= type @active-type)
                           (reset! active-type type)
                           (reset! table-state {:title type :path [] :search ""})))]

    ;; Get the fact types for the session.
    (chan/run-query! [:list-fact-types (:active-session @view-state)]
                     [:list-fact-types (:active-session @view-state)]
                     (fn [results]
                       (reset! fact-types results)))

    ;; When the selected fact ids change, get the new explanation.
    (reagent.ratom/run!
     (chan/run-query! [:explain-facts (:active-session @view-state)]
                      [:explain-facts (:active-session @view-state) @fact-ids]
                      (fn [results] (reset! explanation-graph results))))

    ;; When the active type changes, get the list of facts for it.
    (reagent.ratom/run!
     (when @active-type
       (chan/run-query! [:list-facts-by-type (:active-session @view-state)]
                        [:list-facts-by-type
                         (:active-session @view-state)
                         @active-type
                         {:filter (:search @table-state)}]
                        (fn [results] (reset! fact-list results)))))

    (fn [view-state]
      [:div.container-fluid {:style {:height "100%"}}
       [context-menu fact-ids]
       [:div.row {:style {:height "100%"}}
        [:div.col-lg-3.col-md-3.col-sm-3
         [show-fact-types fact-types on-type-change]]

        [:div.col-lg-9.col-md-9.col-sm-9 {:style {:height "90%"}}

         (when (seq @fact-ids)
           [:div.panel.panel-default {:style {:height "60%"}}
            [:div.panel-heading.clearfix
             [:h4.panel-title.pull-left "Fact Explanation"]
             [:div.input-group.pull-right
              {:style {:width "80px"
                       :float "right"}}
              [:button.btn.btn-sm.btn-default {:onClick (fn [] (reset! fact-ids #{}))} "Clear" ]]]


            [render-explanation-graph]])

         (when @active-type
           [:div (if (seq @fact-ids)
                   {:style {:height "30%"}}
                   {:style {:height "100%"}})
            [rt/render-table
             fact-list
             {:row-id-fn first
              :get-record-fn second
              :on-row-click-fn (fn [row-id] (swap! fact-ids conj row-id))}
             table-state]])]]])))
