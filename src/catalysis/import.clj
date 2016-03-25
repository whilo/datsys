(ns catalysis.import
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]))

(defrecord addTestData [datomic]
  component/Lifecycle
  (start [component]
    (log/info "adding data")
    (let [data (-> "resources/test-data.edn" slurp read-string)]
      @(d/transact (:conn datomic) data)))
  (stop [component]
       component))


(defn add-data []
  (map->addTestData {}))
