(ns clara.tools.graphing
  "Dagre-based graphing to support Clara tools."
  (:require [schema.core :as s]))


(def graph-schema {:nodes
                   {s/Any ; Node ID.
                    {(s/optional-key :label) s/Str ; Node label.
                     (s/optional-key :class) s/Str} ; Space-separated string that can be used as DOM classes.
                    }
                   :edges
                   {s/Any ; Edge ID
                    {:from s/Any ; From node
                     :to s/Any ; To node
                     (s/optional-key :label) s/Str ; Edge label.
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
  (doseq [[id {:keys [from to label class]}] edges]
    (.addEdge digraph id from to (cond-> (js-obj)
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

(def test-graph {:nodes {"Smith" {:label "Mr. Smith"}
                         "Jones" {:label "Mr. Jones"}
                         "Brown" {:label "Mr. Brown"}}
                 :edges {"SJ" {:from "Smith" :to "Jones" :label "Likes"}
                         "SB" {:from "Smith" :to "Brown" :label "Hates"}
                         "BJ" {:from "Brown" :to "Jones" :label "Admires"}}

                 } )

(defn play []
  (let [graph (mk-graph "svg g" test-graph )]
    (render! graph)))

;(play)
