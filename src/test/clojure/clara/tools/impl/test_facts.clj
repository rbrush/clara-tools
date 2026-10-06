(ns clara.tools.impl.test-facts
  (:require [clojure.test :refer :all]
            [clara.rules :refer :all]
            [clara.tools.watch :as wa]
            [clara.tools.impl.facts :as facts]
            [clara.tools.examples.shopping]
            [clara.tools.examples.shopping.records :as rec]))

(deftest test-explain-facts
  (with-open [session (-> (wa/mk-watched-session "Test session" 'clara.tools.examples.shopping :cache false)
                          (insert (rec/->Customer :vip)
                                  (rec/->Purchase 150 :widget))
                          (fire-rules))]
    (let [{:keys [fact-to-id id-to-explanation] :as info} (facts/to-session-info session)
          [vip-discount] (filter #(= (rec/->Discount :vip 10) %) (keys fact-to-id))
          discount-id (fact-to-id vip-discount)
          {:keys [nodes edges]} (#'facts/explanation-graph info [discount-id])]

      (is (some? vip-discount) "VIP discount should be inserted")
      (is (some? (id-to-explanation discount-id)))

      ;; The explained fact and the facts it matched are graph nodes.
      (is (= :fact (get-in nodes [discount-id :type])))
      (is (some #(= (pr-str (rec/->Customer :vip)) (:value %)) (vals nodes)))

      ;; Matched facts connect to their conditions, and conditions to the explained fact.
      (is (some #(= :matches (:type %)) (vals edges)))
      (is (some (fn [[[_ to] {:keys [type]}]] (and (= discount-id to) (= :asserts type))) edges))

      ;; Every edge refers to nodes in the graph.
      (doseq [[[from to] _] edges]
        (is (nodes from))
        (is (nodes to))))))
