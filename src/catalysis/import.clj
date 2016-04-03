(ns catalysis.import
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [catalysis.ws :as ws]
            [clojure.java.io :as io]
            [datsync.server.core :as datsync]
            [com.stuartsierra.component :as component]
            [catalysis.app :as app]))

(defrecord Importer [config datomic]
  component/Lifecycle
  (start [component]
    (log/info "Importering data")
    (let [data (-> "resources/test-data.edn" slurp read-string)]
      @(d/transact (:conn datomic) data)))
  (stop [component]
       component))


(defn new-importer []
  (map->Importer {}))

