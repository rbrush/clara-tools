(ns clara.tools.ui.server
  "Server support for running UI components."

  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [compojure.core :refer [defroutes context GET]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clara.tools.ui.logic :as logic]
            [clara.tools.ui.common :as common]
            [ring.adapter.jetty :as ring]
            [hiccup.page :as page]))

(defroutes routes
  (route/resources "/")
  (GET "/" [] (page/html5
               (common/with-layout
                 "Clara Tools Placeholder!!!"
                 [:div [:h2 "Clara Tools Placeholder"]
                  [:svg {:id "testdraw" :width 500 :height 500}
                   [:g {:transform "translate(20,20)"}]]])))

  (context "/logic" []
           logic/routes))

;; Support for query paramters, session state, etc.
(def app (handler/site routes))

(defonce ^:private server (atom nil))

(defn start-server!
  "Starts a Jetty server to support the tools UI."
  []
  (when (nil? @server)
    (reset! server (ring/run-jetty #'app {:port 8080 :join? false}))))

(defn stop-server!
  []
  (when-let [jetty @server]
    (.stop jetty)
    (reset! server nil)))
