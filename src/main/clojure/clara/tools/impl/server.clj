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
            [cognitect.transit :as transit]
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

(defn- to-transit
  "Converts a data structure to a JSON-encoded transit."
  [data write-handlers]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out
                               :json
                               {:handlers write-handlers})]

    (transit/write writer data)
    (String. (.toByteArray out))))

(defn- from-transit
  "Converts a JSON-encoded transit string into the corresponding data structure."
  [transit-json-string]
  (let [in (java.io.ByteArrayInputStream. (.getBytes ^String transit-json-string))
        reader (transit/reader in :json)]
    (transit/read reader)))

(extend-type AsyncChannel
  q/QueryResponseChannel
  (send-response! [channel key response write-handlers]
    (send! channel (to-transit {:key key :response response} write-handlers)))

  (send-failure! [channel key failure write-handlers]
    (send! channel (to-transit {:key key :error failure}))))

(def running-queries (atom {}))

(defn- handle-request [channel request-string]
  (let [{:keys [type key request] :as message} (from-transit request-string)]
    (if (not (and type key request))
      (println "INVALID MESSAGE:" (pr-str message))
      (try
        (case type

          :start-query (let [cancel-fn (q/run-query request key channel)]
                         (swap! running-queries assoc key cancel-fn))
          :end-query (let [cancel-fn (get @running-queries key)]
                       ;; (cancel-fn request)
                       (swap! running-queries dissoc key)))

        ;; TODO: appropriate logging.
        (catch Exception e
          (println "EXCEPTION" (.getMessage e))
          (.printStackTrace e))))))

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
  [port]
  (when (nil? @server)
    (reset! server (http-kit/run-server app {:port port}))))

(defn stop-server!
  []
  (when @server
    (try
      (@server :timeout 100)
      (catch java.util.concurrent.RejectedExecutionException e
        ))
    (reset! server nil)))
