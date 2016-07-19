(ns clara.tools.client.record-table
  "UI component for display tables of Clojure records or structs."
  (:require [reagent.core :as reagent :refer [atom]]
            [clara.tools.client.bootstrap :as b]))

(defn- get-columns [results path get-record-fn]
  (keys (get-in (get-record-fn (first results)) path)))

(defn render-table
  "Renders the table."
  [records-ref
   {:keys [row-id-fn get-record-fn on-row-click-fn]
    :or {row-id-fn identity get-record-fn identity} :as handlers}
   table-state-ref]

  (let [{:keys [title path search]} @table-state-ref
        columns (get-columns @records-ref path get-record-fn)]
    [:div.panel.panel-default {:style {:height "100%"}}
     [:div.panel-heading.clearfix
      [:h4.panel-title.pull-left title]
      (when search
        [:div.input-group.pull-right
         {:style {:width "200px"
                  :float "right"}}
         [:input {:type "text"
                  :placeholder "search table"
                  :value search
                  :onChange (fn [update] (swap! table-state-ref assoc :search (-> update .-target .-value)))}]])]
     [:div.panel-body.record-table-content
      {:style {:height "100%" :overflow-y "scroll"}}
      (when (seq path)
        (into [:span {:bsSize "small"
                      :onClick (fn [] (swap! table-state-ref assoc :path []))}]
              (concat
               (for [path-item path]
                 [:span " / " (name path-item) " " ])
               [[b/glyphicon {:glyph "remove"}]])))

      (when (seq @records-ref)
        [b/table {:striped true :bordered false}
         [:thead
          (into [:tr]
                (for [column columns]
                  [:th {:onClick (fn [] (swap! table-state-ref update :path conj column))} (name column)]))]
         (into [:tbody]
               (for [row @records-ref
                     :let [sub-row (get-in (get-record-fn row) path)]]
                 (into [:tr (when on-row-click-fn
                              {:onClick (fn [] (on-row-click-fn (row-id-fn row)))})]
                       (for [column columns]
                         [:td (str (get sub-row column))]))))])]]))
