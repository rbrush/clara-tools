(ns clara.tools.client.graphs.graphing
  "Tools to work with graphs."
  (:require [schema.core :as s]))

(defn- walk-graph
  "Walks the graph starting with the given node key and the given function to return
   the edges to walk from a node.

   Parameters are:

   * the graph to walk
   * the node key where we start our walk
   * an edges-from-node function that returns the set of edges to walk from a node.
   * a node-from-edge  function that returns which side of the edge to visit,
     to support walking the graph in either direction."
  [{:keys [nodes edges] :as graph} node-key edges-from-node node-from-edge]

  (loop [[current-edge-key & more-edge-keys] (edges-from-node node-key)

         visited #{}
         result {:nodes {node-key (get nodes node-key)}
                 :edges {}}]

    (if-not current-edge-key
      result

      (if (visited current-edge-key)
        (recur more-edge-keys visited result)
        (let [next-node-key (node-from-edge current-edge-key)]
          (recur (concat more-edge-keys (edges-from-node next-node-key))
                 (conj visited current-edge-key)
                 (merge-with conj
                             result
                             {:nodes {next-node-key (get nodes next-node-key)}
                              :edges {current-edge-key (get edges current-edge-key)}})))))))

(defn connects-to
  "Returns the subset of the graph that transitively connects to the given node key."
  [graph node-key]
  (walk-graph graph
              node-key
              (fn [node-key]
                (filter (fn [[from-key to-key]]
                          (= to-key node-key))
                        (keys (:edges graph))))
              ;; Look at the from part of the edge, which is first in the tuple.
              first))

(defn reachable-from
  "Returns the subset of the graph transitively reachable from the node with the given ID"
  [graph node-key]
  (walk-graph graph
              node-key
              (fn [node-key]
                (filter (fn [[from-key to-key]]
                          (= from-key node-key))
                        (keys (:edges graph))))

              ;; Look at the to part of the edge, which is second in the tuple.
              second))

(defn filter-facts
  "Returns sub-graph that includes only nodes connected to
   the given facts. The given facts-regex is a regular expression
   used to match facts."
  [graph facts-regex]
  ;; Identify nodes matching the facts then graph the sub-graph that
  ;; connects to them.
  (apply merge-with conj
         {:nodes {}
          :edges {}}
         (for [[node-key {:keys [type value] :as node}] (:nodes graph)
               :when (and (= :fact type)
                          (re-find facts-regex value))
               subgraph [(connects-to graph node-key) (reachable-from graph node-key)]]
           subgraph)))
