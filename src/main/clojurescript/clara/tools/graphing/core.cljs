(ns clara.tools.graphing.core
  "Dagre-based graphing to support Clara tools."
  (:require [schema.core :as s])
  (:require-macros [schema.macros :as sm]))

(sm/defschema graph-schema {:nodes ; Nodes are a map of Node IDs to their metadata.
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

(defn- update-dagre!
  "Updates the given digraph with the given graph data."
  [digraph {:keys [nodes edges] :as graph-data}]

  ;; Add nodes to the graph.
  (doseq [[id {:keys [label class]}] nodes]
    (.addNode digraph id (cond-> (js-obj)
                                 label (doto (aset "label" label))
                                 class (doto (aset "nodeclass" class)))))

  ;; Add edges to the graph.
  (doseq [[[from to] {:keys [label class]}] edges]
    (.addEdge digraph (str from "-" to) from to (cond-> (js-obj)
                                                        label (doto (aset "label" label))
                                                        class (doto (aset "edgeclass" class)))))

  digraph)

(defrecord DagreGraph [node digraph renderer graph-data])

(defn mk-graph
  "Returns a mutable Dagre-based graph bound to the given DOM selection."
  ([selection] (mk-graph selection {:nodes {} :edges {}}))
  ([selection graph-data]
     (let [digraph (update-dagre! (js/dagreD3.Digraph.) graph-data)

           layout (-> (js/dagreD3.layout)
                      (.rankDir "LR"))

           renderer (-> (js/dagreD3.Renderer.)
                        (.layout layout))

           default-draw-nodes (.drawNodes renderer)]

       ;; Replace draw nodes function with one that adds CSS class information.
       (.drawNodes renderer
                   (fn [graph root]
                     (let [svg-nodes (default-draw-nodes graph root)]
                       (.each svg-nodes (fn [u] (this-as this
                                                        (.classed
                                                         (js/d3.select this)
                                                         (.-nodeclass
                                                          (.node graph u))
                                                         true))))
                       svg-nodes)))


       (->DagreGraph (js/d3.select selection)
                     digraph
                     renderer
                     graph-data))))


(defn render!
  "Renders the graph at DOM node identified by the given selection."
  [graph]
  (let [{:keys [node digraph renderer]} graph]
    (.run renderer digraph node)))
