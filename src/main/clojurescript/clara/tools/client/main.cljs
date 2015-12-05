(ns clara.tools.client.main
  "Main entrypoint for the Clara Tools application."
  (:require [goog.events :as events]
            [reagent.core :as reagent :refer [atom]]
            [cljsjs.react-bootstrap]
            [clara.tools.client.bootstrap :as b]
            [clara.tools.client.sessions.query-view :as qv]
            [clara.tools.client.sessions.fact-view :as fv]
            [clara.tools.client.sessions.logic-view :as lv]
            [clara.tools.client.channel :as s]
            [secretary.core :as secretary :refer-macros [defroute]])
  (:import goog.History
           goog.history.EventType))

(def app-state (atom {:active-tab :queries
                      ;; Map of session id to information.
                      :sessions {}
                      ;; identifier for the currect active session.
                      :active-session nil}))

(def active-tab (reagent/cursor app-state [:active-tab]))
(def session-map (reagent/cursor app-state [:sessions]))
(def active-session (reagent/cursor app-state [:active-session]))

(def tabs {:queries {:key :queries :title "Queries"}
           :facts {:key :facts :title "Facts"}
           :logic {:key :logic :title "Logic (Experimental)"}})

(defn session-selector []
  (let [sessions (keys @session-map)

        _ (case (count sessions)
            0 (reset! active-session nil)
            1 (reset! active-session (first sessions))
            nil)

        title (if @active-session
                (get @session-map @active-session)
                "No sessions selected.")]

    (into [b/nav-dropdown {:eventKey :session-select :title title}]
          (for [[session-id session-name] @session-map]
            [b/menu-item [:div {:onClick (fn [] (reset! active-session session-id))}
                          session-name]]))))

(defn session-tab
  [session-view-fn]
  (if @active-session
    [session-view-fn @active-session]
    [:p "No session available to inspect."]))


(defn app []
  (s/run-query! :get-sessions
               [:sessions]
               (fn [results]
                 (reset! session-map results)))
  [:div

  [b/navbar

    (into
      [b/nav {:bsStyle "tabs" :activeKey @active-tab }]
      (for [tab-key [:queries :facts :logic]
            :let [{:keys [key title]} (tab-key tabs)]]
        [b/nav-item {:eventKey key :title title :href (str "#/" (name key))} title]))

   [b/nav {:pullRight true} [session-selector]]]
   (when (and @active-tab @active-session)
     (let [tab-state {:active-session @active-session}
           tab-cursor (reagent/cursor app-state [:session-tabs @active-session @active-tab])]

       (swap! tab-cursor merge tab-state)

       (case @active-tab
         :queries [qv/query-view tab-cursor]
         :facts [fv/fact-view tab-cursor]
         :logic [lv/logic-view tab-cursor]
         [:p "Select a tab!"])))])

(reagent/render [app]  (.getElementById js/document "app"))

(defroute "/facts" {:as params}
  (reset! active-tab :facts))

(defroute "/queries" {:as params}
  (reset! active-tab :queries))

(defroute "/logic" {:as params}
  (reset! active-tab :logic))

(defroute "/" {:as params}
  (reset! active-tab :query))



(let [h (History.)]
  (goog.events/listen h "navigate" #(secretary/dispatch! (.-token %)))
  (doto h
    (.setEnabled true)))
