(ns clara.tools.client.graphs.dagre
  "Dagre-based graphing to support Clara tools."
  (:require [schema.core :as s :refer-macros [defschema]]
            ["d3" :as d3]
            ["dagre-d3-es" :refer [graphlib render]]))

(defschema graph-schema {:nodes ; Nodes are a map of Node IDs to their metadata.
                          {s/Any ; Node ID.
                             {(s/optional-key :label) s/Str ; Node label.
                              (s/optional-key :class) s/Str} ; Space-separated string that can be used as DOM classes.
                             }
                            :edges ; Edges are a map of node-id pairs to corresponding metadata.
                            {(s/pair s/Any "from-node-id"
                                     s/Any "to-node-id")
                             {(s/optional-key :label) s/Str ; Edge label.
                              (s/optional-key :class) s/Str  ; Space-separated string that can be used as DOM classes.
                              }}})

(defn- mk-digraph
  "Returns a graphlib graph with the given graph data."
  [{:keys [nodes edges] :as graph-data}]
  (let [^js digraph (new (.-Graph graphlib) #js {:multigraph true})]

    (.setGraph digraph #js {:rankdir "TB"})

    ;; Add nodes to the graph.
    (doseq [[id {:keys [label class]}] nodes]
      (.setNode digraph id (cond-> #js {:label (or label "")}
                             class (doto (aset "class" class)))))

    ;; Add edges to the graph.
    (doseq [[[from to] {:keys [label class]}] edges]
      (.setEdge digraph from to
                (cond-> #js {:label (or label "")}
                  class (doto (aset "class" class)))
                (str from "-" to)))

    digraph))

(defrecord DagreGraph [node digraph options graph-data])

(defn mk-graph
  "Returns a Dagre-based graph bound to the given DOM selection."
  ([selection] (mk-graph selection {:nodes {} :edges {}}))
  ([selection graph-data] (mk-graph selection graph-data {}))

  ([selection graph-data options]
   (->DagreGraph (d3/select selection)
                 (mk-digraph graph-data)
                 options
                 graph-data)))

(defn- enable-zoom!
  "Lets the user drag to pan and scroll to zoom the given group within its SVG.
   The current view is kept when the graph is re-rendered."
  [^js group]
  (let [^js svg (d3/select (.-ownerSVGElement (.node group)))
        zoom (-> (d3/zoom)
                 (.scaleExtent #js [0.1 4])
                 (.on "zoom" (fn [^js event]
                               (.attr group "transform" (.-transform event)))))]
    (if (.attr svg "data-zoom")
      (.call svg zoom)
      (do (.attr svg "data-zoom" "true")
          (.call svg zoom)
          (.call svg (.-transform zoom) (.translate d3/zoomIdentity 20 20))))))

(defn render!
  "Renders the graph at DOM node identified by the given selection."
  [{:keys [node digraph options]}]
  ;; Nothing to draw into, e.g. when there is no explanation to show.
  (when (.node ^js node)
    (let [{:keys [on-node-click on-context-menu]} options]

      (enable-zoom! node)

      ;; Start from an empty group so nodes from a previous render don't linger.
      (.remove (.selectAll node "*"))

      ((render) node digraph)

      (-> node
          (.selectAll "g.node")
          (.on "click" (fn [_event node-key]
                         (when on-node-click
                           (on-node-click node-key))))
          (.on "contextmenu" (fn [event node-key]
                               (.preventDefault event)
                               (when on-context-menu
                                 (on-context-menu {:node-key node-key
                                                   :x (.-clientX event)
                                                   :y (.-clientY event)}))))))))
