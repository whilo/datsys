(ns datview.core
  (:require [clojure.java.io :as io]))


(def schema
  (->> "datview-schema.edn"
       ;; Swap out once a lib XXX TODO
       (str "src/datview/resources/")
       ;io/resource
       slurp
       read-string))

;; Other things for the clj version?
      
