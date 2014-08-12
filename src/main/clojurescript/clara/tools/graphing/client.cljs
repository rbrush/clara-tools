(ns clara.tools.graphing.client
  "Client-side library for retrieving and displaying graphs from a RESTful resource."
  (:require [clara.tools.graphing.core :as graph]
            [ajax.core :refer [GET]]))


(def graph-root "/graphs/")

(defn ^:export display-graph
  "Renders the graph at the specified resource path at the specified DOM selection."
  [graph-name selection]
  (GET (str "/graphs/" graph-name)
       {:handler (fn [graph-data]
                   (-> (graph/mk-graph "svg g" graph-data)
                       (graph/render!))
                   )
        :error-handler #(js/alert %)}))
