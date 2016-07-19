(ns clara.tools.client.graphs.dagre
  "Dagre-based graphing to support Clara tools."
  (:require [schema.core :as s :refer-macros [defschema]]))

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
  ([selection graph-data] (mk-graph selection graph-data {}))

  ([selection {:keys [nodes edges] :as graph-data} {:keys [on-node-click
                                                           on-context-menu
                                                           context-menu-id] :as options}]
     (let [digraph (update-dagre! (js/dagreD3.Digraph.) graph-data)

           layout (-> (js/dagreD3.layout)
                      (.rankDir "TB"))

           renderer (-> (js/dagreD3.Renderer.)
                        (.layout layout))

           default-draw-nodes (.drawNodes renderer)]

       ;; Replace draw nodes function with one that adds CSS class information.
       (.drawNodes
        renderer
        (fn [graph root]
          (let [svg-nodes (default-draw-nodes graph root)]
            (.each svg-nodes
                   (fn [u]
                     (this-as this
                              (let [item (js/d3.select this)]

                                (when on-node-click
                                  (.on item  "click" on-node-click))


                                (.on item "contextmenu"
                                     (fn [node-key]
                                       (js/d3.event.preventDefault)
                                       (when on-context-menu

                                         (on-context-menu {:node-key node-key
                                                           :x (.-clientX  d3.event)
                                                           :y (.-clientY d3.event)})

                                         (comment (-> (js/d3.select (str "#" context-menu-id))
                                                      (.style "position" "absolute")
                                                      (.style "left" (str (.-clientX  d3.event) "px"))
                                                      (.style "top" (str (.-clientY d3.event) "px"))
                                                      (.style "display" "inline-block")
                                                      (.on "mouseleave" (fn [] (-> (js/d3.select (str "#" context-menu-id))
                                                                                  (.style "display" "none")))))))))

                                (.classed
                                 item
                                 (.-nodeclass
                                  (.node graph u))
                                 true)))))
            svg-nodes)))


       (->DagreGraph (js/d3.select selection)
                     digraph
                     renderer
                     graph-data))))


(defn render!
  "Renders the graph at DOM node identified by the given selection."
  [graph]
  (let [{:keys [node digraph renderer]} graph]
    (.run renderer digraph node))


  )
