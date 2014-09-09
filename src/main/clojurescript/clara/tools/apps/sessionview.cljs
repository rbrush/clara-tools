(ns clara.tools.apps.sessionview
  "Clara logic viewer."
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [schema.core :as s]
            [reagent.core :as reagent :refer [atom]]
            [clara.tools.dagre :as dagre]
            [secretary.core :as secretary :include-macros true :refer [defroute]])

  (:require-macros [schema.macros :as sm]))


;; Constant defining an empty graph.
(def empty-graph {:nodes {} :edges {}})

;; Session information.
(def clara-session (atom {}))

;; The graph to display.
(def explain-graph (atom empty-graph))

(defn- explain-fact
  "Appnd or replace the explanation window with an explanation of this fact."
  ([fact-id] (explain-fact fact-id false))
  ([fact-id append]
     (GET (str "/session/" (:id @clara-session) "/fact/" fact-id "/explanation")
          {:handler (fn [graph]
                      (let [merged-graph (if append
                                           (merge-with conj @explain-graph graph)
                                           graph)]
                        (reset! explain-graph merged-graph)))

           :response-format "application/edn"

           :error-handler #(js/alert (str "Error retrieving explanation:" %))})))

(defn fact-list []
  [:div.panel.panel-default
   [:div.panel-heading "Session Facts"]
   [:ul.list-group
    (for [[id fact] (:facts @clara-session)]
      [:li.list-group-item {:on-click #(explain-fact id)}
       fact])]])

(defmulti display-node :type)

(defmethod display-node :condition
  [node]
  {:label (str (get-in node [:value :constraints]))})

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

(defn- handle-node-click [id]

  ;; If the user clicks on a fact, append the explanation
  ;; of that fact to the graph.
  (when (get-in @clara-session [:facts id])
    (explain-fact id true)))

(defn- explanation-info-setup []
  (deref explain-graph)
  [:div
   [:div { ; :style {:height "100%" :width "100%"}
          :react-key "d3-node" ;; ensure React knows this is non-reusable
          :ref "d3-node"       ;; label it so we can retrieve it via get-node
          :id "d3-node"}
    [:svg {:width "100%" :height 800
           :style {:outline "thin solid #C0C0C0"}}

     [:g {:transform "translate(20,20)"}]]]])

(defn render-explanation-graph []
  (let [do-render! #(let [display-graph (session-to-display-graph @explain-graph)
                          dagre-graph (dagre/mk-graph "#d3-node svg g"
                                                      display-graph
                                                      {:on-node-click handle-node-click
                                                       :on-context-menu nil})]
                      (dagre/render! dagre-graph))]

    (reagent/create-class
     {:render explanation-info-setup
      :component-did-mount do-render!
      :component-did-update do-render!})))

(defn sessionview-app []
  [:div
   [:div.container-fluid

    [:div.row
     [:div.col-lg-4.col-md-4.col-sm-4
      [fact-list]
      ]
     [:div.col-lg-8.col-md-8.col-sm-8
      [render-explanation-graph]]]]])

(defroute "/session" {:as params}
  (let [session-id  (get-in params [:query-params :clara-session])]

    ;; Set the namespaces to view.
    (swap! clara-session assoc :id session-id)

    (GET (str "/session/" session-id  "/facts")
         {:handler (fn [facts]
                     (swap! clara-session assoc :facts facts))

          :response-format "application/edn"

          :error-handler #(js/alert (str "Error loading facts:" %))})

    ;; Render the application.
    (reagent/render-component [sessionview-app]  (.getElementById js/document "app"))))
