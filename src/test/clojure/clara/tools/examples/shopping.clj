(ns clara.tools.examples.shopping
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :refer :all]
            [clara.tools.watch :as w]))

;;;; Facts used in the examples below.

(defrecord Order [year month day])

(defrecord Customer [status])

(defrecord Purchase [cost item])

(defrecord Discount [reason percent])

(defrecord Total [total])

(defrecord Promotion [reason type])

;;;; Some example rules. ;;;;

(defn my-sum
  "Returns an accumulator that returns the sum of values of a given field"
  [start-total field]
  (acc/accum
   {:initial-value start-total
    :reduce-fn (fn [total item]
                 (+ total (field item)))
    :retract-fn (fn [total item]
                  (- total (field item)))
    :combine-fn +}))

(defrule total-purchases
  (?total <- (my-sum 1 :cost) :from [Purchase])
  =>
  (insert! (->Total ?total)))

(defrule print-total
  [?t <- Total]
  =>
  (println "TOTAL:" ?t))

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
  [:and
   [Order (= :august month)]
   [Total (> total 200)]]
  =>
  (insert! (->Promotion :free-widget-month :widget)))

(defrule free-lunch-with-gizmo
  "Anyone who purchases a gizmo gets a free lunch."
  [Purchase (= item :gizmo)]
  =>
  (insert! (->Promotion :free-lunch-with-gizmo :lunch)))

(defquery get-promotions
  "Query to find promotions for the purchase."
  []
  [?promotion <- Promotion])

;;;; The section below shows this example in action. ;;;;

(defn print-discounts!
  "Print the discounts from the given session."
  [session]

  ;; Destructure and print each discount.
  (doseq [{{reason :reason percent :percent} :?discount} (query session get-best-discount)]
    (println percent "%" reason "discount"))

  session)

(defrule discount-fun
  [?stuff <- [:or
            [max-discount :from [Discount]]
            [max-discount :from [Discount]]]]
  =>
  (println ?stuff))



(defn print-promotions!
  "Prints promotions from the given session"
  [session]

  (doseq [{{reason :reason type :type} :?promotion} (query session get-promotions)]
    (println "Free" type "for promotion" reason))

  session)

(defn run-examples
  "Function to run the above example."
  []
  (println "VIP shopping example:")
  ;; prints "10 % :vip discount"
  (-> (mk-session 'clara.tools.examples.shopping :cache false) ; Load the rules.
      (insert (->Customer :vip)
              (->Order 2013 :march 20)
              (->Purchase 20 :gizmo)
              (->Purchase 120 :widget)) ; Insert some facts.
      (fire-rules)
      (print-discounts!))

  (println "Summer special and widget promotion example:")
  ;; prints: "20 % :summer-special discount"
  ;;         "Free :lunch for promotion :free-lunch-for-gizmo"
  ;;         "Free :widget for promotion :free-widget-month"
  (-> (mk-session 'clara.tools.examples.shopping :cache false) ; Load the rules.
      (insert (->Customer :vip)
              (->Order 2013 :august 20)
              (->Purchase 20 :gizmo)
              (->Purchase 120 :widget)
              (->Purchase 90 :widget)) ; Insert some facts.
      (fire-rules)
      (print-discounts!)
      (print-promotions!))
  nil)

(comment
  (def sess (-> (w/mk-watched-session "My Test Session."
                                      'clara.tools.examples.shopping :cache false)))

  (def updated-sess (-> sess
                        (insert (->Customer :vip)
                                (->Order 2013 :august 20)
                                (->Purchase 20 :gizmo)
                                (->Purchase 120 :widget)
                                (->Purchase 900 :widget)
                                )
                        fire-rules))

  (def back-to-sess (fire-rules sess))


  (def other-sess (-> (w/mk-watched-session "My Other Test Session."
                                            'clara.tools.examples.shopping :cache false)
                      (insert (->Customer :vip)
                              (->Order 2013 :august 20)
                              (->Purchase 20 :gizmo)
                              (->Purchase 120 :widget)
                              (->Purchase 90 :widget)) ; Insert some facts.
                      (fire-rules)))

  (def more-sess (-> (w/mk-watched-session "My More Session."
                                            'clara.tools.examples.shopping :cache false)
                      (insert (->Customer :vip)
                              (->Order 2013 :august 20)
                              (->Purchase 20 :gizmo)
                              (->Purchase 120 :widget)
                              (->Purchase 90 :widget)) ; Insert some facts.
                      (fire-rules)))

  (w/cancel-watch! sess)

  (w/cancel-watch! other-sess)

  (w/cancel-watch! more-sess)

  (clara.tools.impl.facts/to-session-info other-sess)

  (w/browse!)

  (w/shutdown!)
  )
