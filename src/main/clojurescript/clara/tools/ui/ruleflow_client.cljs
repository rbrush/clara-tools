(ns clara.tools.ui.ruleflow-client
  "Client side support for working with ruleflows."
  (:require [ajax.core :refer [GET POST]]
            [clara.tools.graphing.core :as graph]))

(def graph-root "/logic/graph")

(defn ^:export display-graph
  "Renders the graph at the specified resource path at the specified DOM selection."
  [namespaces selection]
  (GET (str graph-root "?namespaces=" namespaces)
       {:handler (fn [graph-data]
                   (-> (graph/mk-graph "svg g" graph-data)
                       (graph/render!))
                   )
        :error-handler #(js/alert %)}))

(defn ^:export show-logic [namespaces]
  (display-graph namespaces "svg g"))
