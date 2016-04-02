(ns catalysis.import
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [catalysis.ws :as ws]
            [clojure.java.io :as io]
            [datsync.server.core :as datsync]
            [com.stuartsierra.component :as component]))

(defrecord addTestData [datomic]
  component/Lifecycle
  (start [component]
    (log/info "adding data")
    (let [data (-> "resources/test-data.edn" slurp read-string)]
      (datsync/start-transaction-listener! (d/tx-report-queue (:conn datomic)) ws/handle-transaction-report!)
      @(d/transact (:conn datomic) data)))
  (stop [component]
       component))


(defn add-data []
  (map->addTestData {}))

