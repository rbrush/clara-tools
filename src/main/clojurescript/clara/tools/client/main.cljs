(ns clara.tools.client.main
  "Main entrypoint for the Clara Tools application."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.dom.client :as rdc]
            [clara.tools.client.bootstrap :as b]
            [clara.tools.client.sessions.query-view :as qv]
            [clara.tools.client.sessions.fact-view :as fv]
            [clara.tools.client.sessions.logic-view :as lv]
            [clara.tools.client.channel :as s]))

(defonce app-state (atom {:active-tab :queries
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

        ;; If only one session exists, make it the active session.
        _ (case (count sessions)
            0 (reset! active-session nil)
            1 (reset! active-session (first sessions))
            nil)

        title (if @active-session
                (get @session-map @active-session)
                "No sessions selected.")]

    (into [b/nav-dropdown {:title title :id "select-sessions" :align "end"}]
          (for [[session-id session-name] @session-map]
            ^{:key session-id}
            [b/menu-item {:onClick (fn [] (reset! active-session session-id))}
             session-name]))))

(defn session-tab
  [session-view-fn]
  (if @active-session
    [session-view-fn @active-session]
    [:p "No session available to inspect."]))


(defn app []
  [:div {:style {:min-height "100%" :height "100%"}}

   [b/navbar {:bg "light" :className "px-3 mb-2"}

    (into
      [b/nav {:variant "tabs" :activeKey (name @active-tab)}]
      (for [tab-key [:queries :facts :logic]
            :let [{:keys [key title]} (tab-key tabs)]]
        ^{:key key}
        [b/nav-item {:eventKey (name key) :title title :href (str "#/" (name key))} title]))

    [b/nav {:className "ms-auto"} [session-selector]]]
   (when (and @active-tab @active-session)
     (let [tab-state {:active-session @active-session}
           tab-cursor (reagent/cursor app-state [:session-tabs @active-session @active-tab])]

       (swap! tab-cursor merge tab-state)

       (case @active-tab
         :queries [qv/query-view tab-cursor]
         :facts [fv/fact-view tab-cursor]
         :logic [lv/logic-view tab-cursor]
         [:p "Select a tab!"])))])

(def ^:private routes
  {"#/facts" :facts
   "#/queries" :queries
   "#/logic" :logic})

(defn- route! []
  (reset! active-tab (get routes (.-hash js/location) :queries)))

(defonce ^:private root (cljs.core/atom nil))

(defn ^:export mount! []
  (rdc/render @root [app]))

(defn ^:export init []
  (reset! root (rdc/create-root (.getElementById js/document "app")))
  (.addEventListener js/window "hashchange" route!)
  (route!)
  (s/run-query! :get-sessions
                [:sessions]
                (fn [results]
                  (reset! session-map results)))
  (mount!))
