(ns clara.tools.impl.test-watcher
  (:require [clojure.test :refer :all]
            [clara.rules :refer :all]
            [clara.tools.watch :as wa]
            [clara.tools.impl.watcher :as wr]
            [clara.tools.examples.shopping :as shop]
            [clara.tools.examples.shopping.records :as rec]
            [clara.tools.examples.commands :as cmd]))

(deftest test-empty-watch
  (with-open [session (wa/mk-watched-session "Test session" 'clara.tools.examples.shopping :cache false)]
    (is (= '[clara.tools.examples.shopping]
           (.sources session)))

    (is (empty? (wr/facts session)))

    ;; Insertions and retractions are pending until rules are fired,
    ;; so the watcher only sees them afterwards.
    (let [purchases (fn [session]
                      (filterv #(instance? clara.tools.examples.shopping.records.Purchase %)
                               (wr/facts session)))
          test-facts [(rec/->Purchase 100 :gizmo)
                      (rec/->Purchase 150 :widget)]
          session-with-facts (-> session
                                 (insert-all test-facts)
                                 (fire-rules))
          session-with-retractions (-> (apply retract session-with-facts test-facts)
                                       (fire-rules))]

      (is (empty? (purchases (insert-all session test-facts))))

      (is (= test-facts
             (purchases session-with-facts)))

      ;; Facts derived by rules are tracked too.
      (is (some #(instance? clara.tools.examples.shopping.records.Total %)
                (wr/facts session-with-facts)))

      (is (empty? (purchases session-with-retractions))))))

(deftest test-fire-rules-with-opts
  (with-open [session (wa/mk-watched-session "Test session" 'clara.tools.examples.shopping :cache false)]
    (let [fired (-> session
                    (insert (rec/->Purchase 100 :gizmo))
                    (fire-rules {:cancelling true}))]
      (is (= [(rec/->Purchase 100 :gizmo)]
             (filterv #(instance? clara.tools.examples.shopping.records.Purchase %)
                      (wr/facts fired))))
      ;; Fire-rules options are kept in the change log so reloads replay them.
      (is (= {:type :fire-rules :opts {:cancelling true}}
             (last (.-change-log ^clara.tools.impl.watcher.WatchedSession fired)))))))

(defn- wait-for
  "Polls until pred returns true or the timeout elapses, then returns (result-fn)."
  [pred result-fn]
  (let [deadline (+ (System/currentTimeMillis) 15000)]
    (while (and (not (pred)) (< (System/currentTimeMillis) deadline))
      (Thread/sleep 50))
    (result-fn)))

(def initial-test-content
  "(ns clara.tools.test.reload
  (:require [clara.rules :refer :all]))

(defrule add-string
  =>
  (insert! \"Initial\"))

(defquery get-strings
  []
  [?s <- String])")

(def reload-test-content
  "(ns clara.tools.test.reload
  (:require [clara.rules :refer :all]))

(defrule add-string
  =>
  (insert! \"Reload\"))

(defquery get-strings
  []
  [?s <- String])")


(deftest test-file-update
  (let [test-file (.getPath (java.io.File/createTempFile "test" ".clj"))]

    (spit test-file initial-test-content)
    (load-file test-file)

    (with-open [session (-> (wa/mk-watched-session "Test session" 'clara.tools.test.reload :cache false)
                            (fire-rules))]
      (is (= '[clara.tools.test.reload]
             (.sources session)))

      ;; We should see the insertion from the initial file.
      (is (= ["Initial"]
             (wr/facts session)))

      ;; We should see the insertion from the reloaded file. The file is watched
      ;; once the session is created, but the reload happens asynchronously.
      (spit test-file reload-test-content)

      (is (= ["Reload"]
             (wait-for #(= ["Reload"] (wr/facts session))
                       #(wr/facts session)))))))

(deftest test-cancelling-with-rule-retractions
  ;; Facts inserted externally and retracted by a rule while firing with
  ;; :cancelling must not be left in the watched facts.
  (doseq [opts [{} {:cancelling true}]]
    (with-open [session (wa/mk-watched-session "Test session" 'clara.tools.examples.commands :cache false)]
      (let [fired (-> session
                      (insert (cmd/->Command :a))
                      (fire-rules opts))]
        (is (= [(cmd/->Done :a)] (wr/facts fired))
            (str "fire-rules with " opts))))))
