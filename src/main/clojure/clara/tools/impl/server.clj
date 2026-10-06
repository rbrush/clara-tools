(ns clara.tools.impl.server
  "Server making the Clara Tools back end visible to clients."
  (:require [compojure.core :refer [defroutes context GET]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [org.httpkit.server :as http-kit :refer [send!]]
            [hiccup.page :as page]
            [clara.tools.queries :as q]
            [clara.tools.impl.facts :as facts]
            [clara.tools.impl.logic :as logic]
            [cognitect.transit :as transit]
            [clojure.edn :as edn])

  (:import [org.httpkit.server AsyncChannel]))

(def main-page
  (page/html5 [:head
               [:meta {:charset "utf-8"}]
               [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
               [:link {:href  "/public/css/bootstrap.min.css" :rel "stylesheet" :type "text/css"}]
               [:link {:href  "/public/css/clara-tools.css" :rel "stylesheet" :type "text/css"}]
               ;; No favicon; avoids a 404 request from the browser.
               [:link {:rel "icon" :href "data:,"}]
               [:title "Clara Tools"]]
              [:body
               [:div {:id "app"}]
               [:script {:src "/public/js/main.js"}]]))

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
    (send! channel (to-transit {:key key :error failure} write-handlers))))

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
  (http-kit/as-channel request
                       {:on-open (fn [channel] (swap! channels conj channel))
                        :on-close (fn [channel _status] (swap! channels disj channel))
                        :on-receive (fn [channel message] (handle-request channel message))}))

(defroutes routes
  (route/resources "/public/")
  (GET "/" [] main-page )
  (GET "/socket" request (ws-handler request)))

;; Support for query paramters, session state, etc.
(def app (wrap-defaults routes (-> site-defaults
                                   ;; Static resources are served by the routes above.
                                   (assoc :static false)
                                   (assoc-in [:security :anti-forgery] false))))

(defonce ^:private server (atom nil))

(def server-defaults {:port 8080 :host "127.0.0.1"})

(defn start-server!
  "Starts an http-kit server to support the tools UI.  Optionally takes a
  map of http-kit server options and merges it with the clara-tools defaults;
  consult the http-kit documentation for a list of valid options.  Note that by default
  the server only accepts connections from localhost."
  ([] (start-server! server-defaults))
  ([server-opts]
   (when (nil? @server)
     (reset! server (http-kit/run-server #'app (merge server-defaults server-opts))))))

(defn server-port
  "Returns the port the tools server is listening on, or nil if it is not running."
  []
  (some-> @server meta :local-port))

(defn stop-server!
  []
  (when @server
    (try
      (@server :timeout 100)
      (catch java.util.concurrent.RejectedExecutionException e
        ))
    (reset! server nil)))
