(ns clara.tools.client.sessions.query-view
  "Tab to explore Clara queries."
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as ratom]
            [clara.tools.client.bootstrap :as bs]
            [clara.tools.client.record-table :as rt]
            [clara.tools.client.channel :as chan]))

(defn query-list [queries on-run-query]
  [bs/table
   {:striped true :bordered true}
   [:thead
    [:tr
     [:th "Name"]
     [:th "Params"]
     [:th "Documentation"]
     [:th "Run"]]
    (into [:tbody]
          (for [query @queries]
            [:tr
             [:td (:name query)]
             [:td (if (seq? (:params query))
                    (:params query)
                    "none")]
             [:td (:doc query)]
             [:td [bs/button
                   {:onClick #(on-run-query query)}
                   [:em ">"]]]]))]])

(defn query-view [view-state]

  (let [session-queries (atom [])
        active-query (reagent/cursor view-state [:active-query])
        active-session (reagent/cursor view-state [:active-session])
        active-query-results (atom [])
        table-state (atom nil)
        on-query-change (fn [query]
                          (when (not= query @active-query)
                            (reset! active-query (:name query))
                            (reset! table-state {:title @active-query :path [] :search ""})))]

    ;; Run the initial query to get our list of Clara queries.
    (chan/run-query! [:get-queries @active-session]
                     [:queries @active-session]
                     (fn [response]
                       (reset! session-queries response)))

    ;; Re-run the current query when it or the table state changes.
    (reagent.ratom/run!
     (when @active-query
       (chan/run-query! [:run-active-query @active-session]
                        ;; TODO: include query parameters...
                        [:query @active-session @active-query {} {:filter (:search @table-state)}]
                        (fn [response]
                          (reset! active-query-results response)))))

    (fn [view-state]
      [bs/grid
       [bs/row
        [:h4 "Queries"]
        [query-list
         session-queries
         on-query-change]
        (when @table-state
          [rt/render-table
           active-query-results
           {}
           table-state]
          )]])))
