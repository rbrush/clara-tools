(ns clara.tools.client.channel
  "Channel to interact with the Clara tools back end."
  (:require [cljs.reader :as reader]))

;; Atom containing the web socket connection to th eback end.
(defonce ^:private channel (atom nil))

;; TODO: place in query response so UI can handle them...
(defrecord ErrorResponse [response])

;; Map of query keys to handlers.
(def query-handlers (atom {}))

(defn- receive-message [message]

  (when (.-data message)
    (let [{:keys [query key response error] :as message} (reader/read-string (.-data message))]

      (if error

        ;; TODO: display error message in a non-obtrusive way.
        (.log js/console (str "Error from server" error))

        (if-let [on-success (get-in @query-handlers [key :on-success])]

          (on-success response)
          (js/alert (str "Unknown handler for " key)))))))

(defn- connect! []
  (if-let [chan (js/WebSocket. (str "ws://" (.-host js/location) "/socket") )]
    (do
      (set! (.-onmessage chan) receive-message)
      (reset! channel chan))
    (throw (js/Error. "Unable to create web socket."))))

(defn- send-message!
  [message]
  (if (and @channel (= 1 (.-readyState @channel)))
    (do

      (.log js/console (str "Sending: " (pr-str message)))
      (.send @channel (pr-str message)))

    (do
      ;; Connect if we haven't done so already.
      (when (nil? @channel)
        (connect!))

      ;; Wait for channel to become available and then send a message.
      (js/setTimeout (fn [] (send-message! message)), 100))))


(defn run-query!
  ([key query on-success]
   (run-query! key query
               on-success
               (fn [failure]
                 (js/alert (str "FAILURE:" (pr-str failure))))))

  ([key query on-success on-failure]
   (swap! query-handlers assoc key {:query query
                                    :on-success on-success
                                    :on-failure on-failure})
   (send-message! {:type :start-query :key key :request query})))

(defn cancel-query! [key]
  "Cancels the query with the given key."

  (swap! query-handlers dissoc key)
  (send-message! {:type :end-query :key key}))

(defn format-error [response]
  [:div [:h4 "Unable to run query: " (pr-str response)]])
