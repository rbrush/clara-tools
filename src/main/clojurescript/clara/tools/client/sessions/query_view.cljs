(ns clara.tools.client.sessions.query-view
  "Tab to explore Clara queries."
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as ratom]
            [clara.tools.client.bootstrap :as bs]
            [clara.tools.client.record-table :as rt]
            [clara.tools.client.channel :as chan]
            [clojure.string :as string]
            [cljs.reader :as reader]))

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
          (for [query queries]
            [:tr
             [:td (:name query)]
             [:td (if (seq (:params query))
                    (string/join " " (:params query))
                    "none")]
             [:td (:doc query)]
             [:td [bs/button
                   {:onClick #(on-run-query query)}
                   [:em ">"]]]]))]])

(defn- query-params [query-params]
  "Query params is a map of parameter names to values."
  (when (seq @query-params)
    [bs/grid

     [bs/row
      (map
       (fn [[name value]]
         [bs/panel {:header "Query parameters"}
          [bs/input {:type "text"
                     :label (pr-str name)
                     :labelClassName "col-md-1"
                     :wrapperClassName "col-md-2"
                     :value (str value)
                     :onChange (fn [update]
                                 (let [new-val (reader/read-string (-> update .-target .-value))
                                       new-val (if (symbol? new-val) (name new-val) new-val)]
                                   (swap! query-params
                                          assoc name new-val)))}]])
       @query-params)]]))

(defn- run-query [queries active-query active-session active-query-results-ref]
  (let [active-query-params (get-in queries [active-query :params])]

    (chan/run-query! [:run-active-query active-session]
                     ;; TODO: include query parameters...
                     [:query active-session
                      active-query
                      active-query-params
                      {:filter (get-in queries [active-query :table-state :search])}]
                     (fn [response]
                       (reset! active-query-results-ref response)))))

(defn query-view [view-state]
  (let [session-queries (reagent/cursor view-state [:queries])
        active-query (reagent/cursor view-state [:active-query])
        active-session (reagent/cursor view-state [:active-session])
        active-query-results (atom [])
        on-query-change (fn [query]
                          (when (not= query @active-query)

                            (reset! active-query (:name query))))]

    ;; Get the list of queries available in the session if we haven't already.
    (when-not (:queries @view-state)
      (chan/run-query! [:get-queries @active-session]
                       [:queries @active-session]
                       (fn [response]

                         (swap! view-state assoc :queries
                                (into {}
                                      (for [query response]
                                        [(:name query) {:table-state
                                                        {:title (:name query)
                                                         :search ""
                                                         :path []}
                                                        :query-info query
                                                        ;; Create an empty paramater map.
                                                        :params (into {}
                                                                      (for [param (:params query)]
                                                                        {param nil}))}
                                         ]))))))


    ;; Re-run the current query when it or the table state changes.
    (reagent.ratom/run!
     (when @active-query
       (run-query @session-queries @active-query @active-session active-query-results)))

    (fn [view-state]
      (let [table-state (reagent/cursor view-state [:queries @active-query :table-state])
            active-query-params (reagent/cursor view-state [:queries @active-query :params])]

        [bs/grid
         [bs/row
          [:h4 "Queries"]
          [query-list
           (for [query-seq (vals (:queries @view-state))]
             (:query-info query-seq))
           on-query-change]
          (when @active-query
            [:div
             [query-params active-query-params]
             [rt/render-table
              active-query-results
              {}
              table-state]])]]))))
