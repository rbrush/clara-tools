(ns clara.tools.impl.test-watcher
  (:require [clojure.test :refer :all]
            [clara.rules :refer :all]
            [clara.tools.watch :as wa]
            [clara.tools.impl.watcher :as wr]
            [clara.tools.examples.shopping :as shop]
            [clara.tools.examples.shopping.records :as rec]))

(deftest test-empty-watch
  (with-open [session (wa/mk-watched-session "Test session" 'clara.tools.examples.shopping :cache false)]
    (is (= '[clara.tools.examples.shopping]
           (.sources session)))

    (is (empty? (wr/facts session)))

    (let [test-facts [(rec/->Purchase 100 :gizmo)
                      (rec/->Purchase 150 :widget)]
          session-with-facts (insert-all session
                                         test-facts)
          session-with-retractions (apply retract session-with-facts test-facts)]

      (is (= test-facts
             (wr/facts session-with-facts)))

      (is (empty? (wr/facts session-with-retractions))))))

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

      ;; Delay to ensure listener is watching file. TODO: find a way to remove this need.
      (Thread/sleep 2000)

      ;; We should see the insertion from the reloaded file.
      (spit test-file reload-test-content)

      (Thread/sleep 5000)

      (is (= ["Reload"]
             (wr/facts session))))))
