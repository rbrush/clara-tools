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
          (for [query (vals @queries)]
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

(defn query-view [view-state]

  (let [session-queries (atom {})
        active-query (reagent/cursor view-state [:active-query])
        active-session (reagent/cursor view-state [:active-session])
        active-query-params (atom {})
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
                       (reset! session-queries (into {}
                                                     (for [query  response]
                                                       [(:name query) query])))))

    ;; Re-run the current query when it or the table state changes.
    (reagent.ratom/run!
     (when @active-query
       ;; Reset the query parameters if they have changed.
       (when (not= (set (keys @active-query-params))
                   (get-in @session-queries [@active-query :params]))
         (reset! active-query-params
                 (into {}
                       (for [param-name (get-in @session-queries [@active-query :params])]
                         [param-name ""]))))

       (chan/run-query! [:run-active-query @active-session]
                        ;; TODO: include query parameters...
                        [:query @active-session
                         @active-query
                         @active-query-params
                         {:filter (:search @table-state)}]
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
          [:div
           [query-params active-query-params]
           [rt/render-table
            active-query-results
            {}
            table-state]])]])))
