(ns clara.tools.apps.sessionview
  "Clara logic viewer."
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [schema.core :as s]
            [reagent.core :as reagent :refer [atom]]
            [clara.tools.dagre :as dagre]
            [secretary.core :as secretary :include-macros true :refer [defroute]])

  (:require-macros [schema.macros :as sm]))

(def clara-session (atom {}))

(def explain-graph (atom nil))

(defn- explain-fact
  [fact-id]
  (GET (str "/session/" (:id @clara-session) "/fact/" fact-id "/explanation")
       {:handler (fn [graph]
                   (reset! explain-graph graph))

        :response-format "application/edn"

        :error-handler #(js/alert (str "Error retrieving explanation:" %))}))

(defn fact-list []
  [:div.panel.panel-default
   [:div.panel-heading "Session Facts"]
   [:ul.list-group
    (for [[id fact] (:facts @clara-session)]
      [:li.list-group-item {:on-click #(explain-fact id)}
       fact])]])

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

(defn- session-to-display-graph
  "Converts a graph of session data to a displayable form."
  [{:keys [nodes edges] :as session-graph}]
  {:nodes (into {}
                (for [[node-id node]  nodes]
                  [node-id {:label (str (:value node))}]))
   :edges (into {}
                (for [[edge-tuple edge] edges]
                  [edge-tuple {:label (name (:type edge))}]))})

(defn render-explanation-graph [on-context-menu]
  (let [do-render! #(let [display-graph (session-to-display-graph @explain-graph)
                          dagre-graph (dagre/mk-graph "#d3-node svg g"
                                                      display-graph
                                                      {:on-node-click nil
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
