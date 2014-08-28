(ns clara.tools.test-logic-graph
  (:require [clara.tools.logic-graph :refer :all]
            [clojure.test :refer :all]
            [clara.rules.accumulators :as acc]
            [clara.tools.ui :as ui]
            [clara.rules :refer :all]))

(defrecord Order [year month day])

(defrecord Customer [status])

(defrecord Purchase [cost item])

(defrecord Discount [reason percent])

(defrecord Total [total])

(defrecord Promotion [reason type])

;;;; Some example rules. ;;;;

(defrule total-purchases
  "Total purchases."
  (?total <- (acc/sum :cost) :from [Purchase])
  =>
  (insert! (->Total ?total)))

;;; Discounts.
(defrule summer-special
  "Place an order in the summer and get 20% off!"
  [Order (#{:june :july :august} month)]
  =>
  (insert! (->Discount :summer-special 20)))

(defrule vip-discount
  "VIPs get a discount on purchases over $100. Cannot be combined with any other discount."
  [Customer (= status :vip)]
  [Total (> total 100)]
  =>
  (insert! (->Discount :vip 10)))

(def max-discount
  "Accumulator that returns the highest percentage discount."
  (acc/max :percent :returns-fact true))

(defquery get-best-discount
  "Query to find the best discount that can be applied"
  []
  [?discount <- max-discount :from [Discount]])

;;; Promotions.
(defrule free-widget-month
  "All purchases over $200 in August get a free widget."
  [Order (= :august month)]
  [Total (> total 200)]
  =>
  (insert! (->Promotion :free-widget-month :widget)))

(defrule free-lunch-with-gizmo
  "Anyone who purchases a gizmo gets a free lunch."
  [Purchase (= item :gizmo)]
  =>
  (insert! (->Promotion :free-lunch-with-gizmo :lunch)))

(defrule free-sticker-with-gizmo-or-widget
  "Free sticker with gizmoe or widget"
  [:or [Purchase (= item :gizmo)]
       [Purchase (= item :widget)]]
  =>
  (insert! (->Promotion :free-gizmo-or-widget :sticker)))

(defquery get-promotions
  "Query to find promotions for the purchase."
  []
  [?promotion <- Promotion])

(deftest test-logic-graph
  "All edges in the graph should have a node."
  (let [{:keys [ nodes edges]} (logic-graph ['clara.tools.test-logic-graph])]

    (doseq [[[from to] value] edges]
      (is (nodes from) (str "Expected " from " in graph for value " value))
      (is (nodes to) (str "Expected " to " in graph for value " value)))))
