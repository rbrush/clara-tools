(ns clara.tools.client.sessions.logic-view
  "View to explore Clara logic."
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [clara.tools.client.bootstrap :as bs]
            [clara.tools.client.record-table :as rt]
            [clara.tools.client.channel :as chan]
            [clara.tools.client.graphs.graphing :as graphing]
            [clara.tools.client.graphs.dagre :as dagre]))


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


;; The context menu for the selected node...
(def context-details (atom nil))

(defn modal
  "Returns a component representing a model."
  [{:keys [title content footer close-fn]}]
  [:div
   [:div.modal-backdrop.fade.in]
   [:div.modal.fade.in {:tabIndex "-1" :role "dialog" :style {:display "block"} :on-click close-fn}
    [:div.modal-dialog
     [:div.modal-content
      [:div.modal-header
       [:h4.modal-title title]]
      content
      [:div.modal-footer
       footer
       [:div.btn.btn-default {:type "button"
                              :on-click close-fn}
        "Close"]]]]]])


(defn focused-facts-list []
  (let [facts @focused-facts]
    [:div.panel.panel-default
     [:div.panel-heading "Focused Fact Types"]
     [:ul.list-group
      (if (empty? facts)
        [:li.list-group-item "<none>"]
        (for [{:keys [name enabled] :as fact} facts]
          [:li.list-group-item
           (last (str/split name "." ))
           [:span.glyphicon.glyphicon-remove.pull-right
            {:on-click #(reset! focused-facts (remove (fn [old-fact] (= fact old-fact)) facts) )}]]))]]))


(defmulti context-menu-content :type)

(defmethod context-menu-content :fact [node]
  [:div.btn-group.btn-group-vertical

   [:button.btn.btn-default
    {:type "button"
     :on-click #(swap! focused-facts conj {:name (:value node) :enabled true})}
    "Add to focus"]])

(defn context-menu []
  (when-let [{:keys [x y node-key] :as details} @context-details]
    [:div {:style {:position "absolute"
                   :left (str (- x 10) "px")
                   :top (str (- y 5) "px")
                   :display "inline-block"
                   :z-index 1000}
           :on-mouse-leave #(reset! context-details nil)}
     (context-menu-content (get-in @logic-graph [:nodes node-key]))]))

(defn logicview-app []
  [:div
   [context-menu]
   [:div.container-fluid

    [:div.row
     [:div.col-lg-2.col-md-2.col-sm-2
      [focused-facts-list]
      ]
     [:div.col-lg-10.col-md-10.col-sm-10

      [render-logic-graph #(reset! context-details %)]]]]])


(defn show-logic [logic-graph-param]
  (reset! logic-graph @logic-graph-param)
  [logicview-app])

(defn logic-view [view-state]
  (let [logic-graph (atom nil)]

    (fn [view-state]

      (chan/run-query! [:logic-graph (:active-session @view-state)]
                       [:logic-graph (:active-session @view-state)]
                       (fn [results] (reset! logic-graph results)))

      [show-logic logic-graph])))
