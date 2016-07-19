(ns clara.tools.examples.shopping.records
  "Records used in the shopping example.")

(defrecord Order [year month day])

(defrecord Customer [status])

(defrecord Purchase [cost item])

(defrecord Discount [reason percent])

(defrecord Total [total])

(defrecord Promotion [reason type])

(defrecord Person [first last middle age gender street city state zip status])
