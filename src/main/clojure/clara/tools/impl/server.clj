(ns clara.tools.impl.server
  "Server making the Clara Tools back end visible to clients."
  (:require [compojure.core :refer [defroutes context GET]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [org.httpkit.server :as http-kit
             :refer [send! with-channel on-close on-receive]]
            [hiccup.page :as page]
            [clara.tools.queries :as q]
            [clara.tools.impl.facts :as facts]
            [clara.tools.impl.logic :as logic]
            [clojure.edn :as edn])

  (:import [org.httpkit.server AsyncChannel]))

(def main-page
  (page/html5 [:head
               [:link {:href  "/public/css/bootstrap.min.css" :rel "stylesheet" :type "text/css"}]
               [:link {:href  "/public/css/clara-tools.css" :rel "stylesheet" :type "text/css"}]
               [:title "Clara Tools"]
               [:body
                [:div {:id "app"}]
                [:div
                 [:script {:src "/public/js/d3.js"}]
                 [:script {:src "/public/js/dagre-d3.js"}]
                 [:script {:src "/public/js/clara-tools.js"}]]]]))

(defonce channels (atom #{}))

(extend-type AsyncChannel
  q/QueryResponseChannel
  (send-response! [channel key response]
    (send! channel (pr-str {:key key :response response})))

  (send-failure! [channel key failure]
    (send! channel (pr-str {:key key :error failure}))))

(def running-queries (atom {}))

(defn- handle-request [channel request-string]
  (let [{:keys [type key request] :as message} (edn/read-string request-string)]
    (if (not (and type key request))
      (println "INVALID MESSAGE:" (pr-str message))
      (case type

        :start-query (let [cancel-fn (q/run-query request key channel)]
                       (swap! running-queries assoc key cancel-fn))
        :end-query (let [cancel-fn (get @running-queries key)]
                     ;; (cancel-fn request)
                     (swap! running-queries dissoc key))))))

(defn ws-handler [request]
  (with-channel request channel
    (swap! channels conj channel)
    (on-close channel (fn [channel] swap! channels #(remove #{channel} %)))
    (on-receive channel (fn [request] (handle-request channel request)))))

(defroutes routes
  (route/resources "/public/")
  (GET "/" [] main-page )
  (GET "/socket" request (ws-handler request)))

;; Support for query paramters, session state, etc.
(def app (handler/site routes))

(defonce ^:private server (atom nil))

(defn start-server!
  "Starts an http-kit server to support the tools UI."
  []
  (when (nil? @server)
    (reset! server (http-kit/run-server app {:port 8080}))))

(defn stop-server!
  []
  (when @server
    (try
      (@server :timeout 100)
      (catch java.util.concurrent.RejectedExecutionException e
        ))
    (reset! server nil)))
