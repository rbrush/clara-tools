(ns clara.tools.ui.server
  "Server support for running UI components."

  (:require [compojure.core :refer [defroutes context GET]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clara.tools.ui.logic :as logic]
            [clara.tools.ui.session :as session]
            [ring.adapter.jetty :as ring]
            [hiccup.page :as page]
            [clojure.java.io :as io])
  (:import [clojure.lang RT]
           [java.io LineNumberReader InputStreamReader PushbackReader]))

;  svg { overflow: hidden; position:fixed; top:0; left:0; height:100%; width:100% }

;; TODO: externalize?
(def style
"
html, body { margin:0; padding:0; overflow:hidden }


.node rect {
    stroke: #333;
    stroke-width: 1.5px;
    fill: #fff;
}

.edgeLabel rect {
    fill: #fff;
}

.edgePath {
    stroke: #333;
    stroke-width: 1.5px;
    fill: none;
}
")

(def version "0.1.1")

(def main-page
  (page/html5 [:head
               [:style style]
               [:link {:href  "css/bootstrap.min.css" :rel "stylesheet" :type "text/css"}]
               [:title "Clara Tools"]
               [:body
                [:nav {:class "navbar navbar-default" :role "navigation"}
                 [:div {:class "container-fluid"}
                  [:a {:class "navbar-brand" :href "#"} "Clara Tools" ]
                  [:p {:class "navbar-text navbar-right"}  (str "Version " version)]]]
                [:div
                 [:div {:id "app"}]]
                [:div
                 [:script {:src "http://fb.me/react-0.11.1.js"}]
                 [:script {:src "/js/react-bootstrap.min.js"}]
                 ;;   [:script {:src "/js/d3.v3.min.js"}]
                 [:script {:src "/js/d3.js"}]
                 [:script {:src "/js/dagre-d3.js"}]
                 [:script {:src "/js/clara-tools.js"}]]]]))


(defn source-for-sym
  "Customized version of Clojure's source function that handles
   items not on the classpath."
  [x]
  (when-let [v (resolve x)]
    (when-let [filepath (:file (meta v))]
      (when-let [strm (or (.getResourceAsStream (RT/baseLoader) filepath)
                          (io/input-stream filepath)
                          )]
        (with-open [rdr (LineNumberReader. (InputStreamReader. strm))]
          (dotimes [_ (dec (:line (meta v)))] (.readLine rdr))
          (let [text (StringBuilder.)
                pbr (proxy [PushbackReader] [rdr]
                      (read [] (let [i (proxy-super read)]
                                 (.append text (char i))
                                 i)))]
            (read (PushbackReader. pbr))
            (str text)))))))



(defroutes routes
  (route/resources "/")
  (GET "/" [] main-page )
  (GET "/source/:source" [source]
       (if-let [source (source-for-sym (symbol source))]
         {:status 200
          :headers {"Content-Type" "text/plain"}
          :body source}
         {:status 404
          :body (str "Unable to load source for " source)}))

  (context "/logic" []
           logic/routes)

  (context "/session" []
           session/routes))

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
