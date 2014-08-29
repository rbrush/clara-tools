(ns clara.tools.apps.logicview
  "Clara logic viewer."
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [schema.core :as s]
            [reagent.core :as reagent :refer [atom]]
            [clara.tools.apps.logicview.graph :as graph]
            [secretary.core :as secretary :include-macros true :refer [defroute]])

  (:require-macros [schema.macros :as sm]))

;; The set of rulesets we are using.
(def rulesets (atom []))

;; The context menu for the selected node...
(def context-details (atom nil))

;; The source code to be displayed in a modal.
(def source-modal-display (atom nil))

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

(defn source-modal []
  (when-let [{:keys [symbol source-code] :as source } @source-modal-display]
    (.log js/console source-code)
    (modal {:title symbol
            :content [:pre (str source-code)
                      ]
            :footer nil
            :close-fn #(reset! source-modal-display nil)})))

(defn ruleset-list []
  (let [rulesets @rulesets]
    [:div.panel.panel-default
     [:div.panel-heading "Rulesets"]
     [:ul.list-group
      (if (empty? rulesets)
        [:li.list-group-item "<none>"]
        (for [{:keys [name enabled]} rulesets]
          ^{:key name} [:li.list-group-item name]))]]))


(defn focused-facts-list []
  (let [facts @graph/focused-facts]
    [:div.panel.panel-default
     [:div.panel-heading "Focused Fact Types"]
     [:ul.list-group
      (if (empty? facts)
        [:li.list-group-item "<none>"]
        (for [{:keys [name enabled] :as fact} facts]
          [:li.list-group-item
           (last (str/split name "." ))
           [:span.glyphicon.glyphicon-remove.pull-right
            {:on-click #(reset! graph/focused-facts (remove (fn [old-fact] (= fact old-fact)) facts) )}]]))]]))

(defn update-namespaces!
  [namespaces]
  (GET (str "/logic/graph?namespaces=" (str/join "+" namespaces))
       {:handler (fn [graph-data]
                   (reset! rulesets
                           (for [namespace namespaces]
                             {:name namespace
                              :enabled true}))

                   (reset! graph/logic-graph graph-data))

        :error-handler #(js/alert (str "Error getting logic graph:" %))}))


(defn show-source [symbol]
  (GET (str "/source/" (js/encodeURIComponent symbol))
       {:handler (fn [source]
                   (reset! source-modal-display {:symbol symbol :source-code source}))

        :response-format "text/plain"

        :error-handler #(js/alert (str "Error getting logic graph:" %))}))

(defmulti context-menu-content :type)

(defmethod context-menu-content :fact [node]
  [:div.btn-group.btn-group-vertical
   [:button.btn.btn-default
    {:type "button"
     :on-click (fn []
                 (show-source (:symbol  node)))}
    "Show source"]

   [:button.btn.btn-default
    {:type "button"
     :on-click #(swap! graph/focused-facts conj {:name (:value node) :enabled true})}
    "Add to focus"]])

(defmethod context-menu-content :default [node]
  [:div.btn-group.btn-group-vertical
   [:button.btn.btn-default
    {:type "button"
     :on-click (fn []
                 (show-source (:symbol  node)))}
    "Show source"]])

(defn context-menu []
  (when-let [{:keys [x y node-key] :as details} @context-details]
    [:div {:style {:position "absolute"
                   :left (str (- x 10) "px")
                   :top (str (- y 5) "px")
                   :display "inline-block"
                   :z-index 1000}
           :on-mouse-leave #(reset! context-details nil)}
     (context-menu-content (get-in @graph/logic-graph [:nodes node-key]))]))

(defn logicview-app []
  [:div
   [context-menu]
   [source-modal]
   [:div.container-fluid

    [:div.row
     [:div.col-lg-2.col-md-2.col-sm-2
      [ruleset-list]
      [focused-facts-list]
      ]
     [:div.col-lg-10.col-md-10.col-sm-10

      [graph/render-logic-graph #(reset! context-details %)]]]]])


(defroute "/logic" {:as params}
  (let [namespaces (str/split (get-in params [:query-params :namespaces]) "+")]

    ;; Set the namespaces to view.
    (update-namespaces! namespaces)

    ;; Render the application.
    (reagent/render-component [logicview-app]  (.getElementById js/document "app"))))
