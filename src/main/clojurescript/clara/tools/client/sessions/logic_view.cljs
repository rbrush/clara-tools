(ns clara.tools.client.sessions.logic-view
  "View to explore Clara logic."
  (:refer-clojure :exclude [atom])
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

(defmethod display-node :exists
  [node]
  {:label "EXISTS"})

(defmethod display-node :test
  [node]
  {:label (str "TEST " (pr-str (get-in node [:value :constraints])))})

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
   [:div {:id "d3-node"}
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
     {:reagent-render render-logic-graph-setup
      :component-did-mount do-render!
      :component-did-update do-render!})))


;; The context menu for the selected node...
(def context-details (atom nil))

(defn focused-facts-list []
  (let [facts @focused-facts]
    [:div.card
     [:div.card-header "Focused Fact Types"]
     [:ul.list-group
      (if (empty? facts)
        [:li.list-group-item "<none>"]
        (for [{:keys [name enabled] :as fact} facts]
          ^{:key name}
          [:li.list-group-item
           (last (str/split name "." ))
           [:span.float-end
            {:role "button"
             :title "Remove"
             :on-click #(reset! focused-facts (remove (fn [old-fact] (= fact old-fact)) facts) )}
            "\u00d7"]]))]]))


(defmulti context-menu-content :type)

(defmethod context-menu-content :fact [node]
  [:div.btn-group-vertical

   [:button.btn.btn-light.border
    {:type "button"
     :on-click #(swap! focused-facts conj {:name (:value node) :enabled true})}
    "Add to focus"]])

;; Only fact nodes have a context menu.
(defmethod context-menu-content :default [_node]
  nil)

(defn context-menu []
  (when-let [content (some->> @context-details
                              :node-key
                              (get (:nodes @logic-graph))
                              (context-menu-content))]
    (let [{:keys [x y]} @context-details]
    [:div {:style {:position "absolute"
                   :left (str (- x 10) "px")
                   :top (str (- y 5) "px")
                   :display "inline-block"
                   :z-index 1000}
           :on-mouse-leave #(reset! context-details nil)}
     content])))

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
