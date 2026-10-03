(ns clara.tools.client.record-table
  "UI component for display tables of Clojure records or structs."
  (:refer-clojure :exclude [atom])
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
    [:div.card {:style {:height "100%"}}
     [:div.card-header.d-flex.align-items-center
      [:h5.card-title.mb-0 title]
      (when search
        [:div.ms-auto
         {:style {:width "200px"}}
         [:input.form-control.form-control-sm
                 {:type "text"
                  :placeholder "search table"
                  :value search
                  :onChange (fn [update] (swap! table-state-ref assoc :search (-> update .-target .-value)))}]])]
     [:div.card-body.record-table-content
      {:style {:height "100%" :overflow-y "scroll"}}
      (when (seq path)
        (into [:span {:role "button"
                      :onClick (fn [] (swap! table-state-ref assoc :path []))}]
              (concat
               (for [path-item path]
                 [:span " / " (name path-item) " " ])
               [[:span.ms-1 {:title "Clear path"} "\u00d7"]])))

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
