(ns catalysis.system
  "System constructor."
  (:require [com.stuartsierra.component :as component]
            [catalysis.ws :as ws]
            [catalysis.config :as config]
            [catalysis.datomic :as datomic]
            [catalysis.server :as server]
            [catalysis.app :as app]
            [catalysis.import :as imp]
            ))

(defn create-system
  ([config-overrides]
   (component/system-map
     :config (config/create-config config-overrides)
     :ws-connection (component/using (ws/new-ws-connection) [:config])
     :http-server (component/using (server/new-http-server) [:config :ws-connection])
     :datomic (component/using (datomic/create-datomic) [:config])
     :import-data (component/using (imp/add-data) [:datomic :ws-connection])
     :app (component/using (app/new-app) [:config :ws-connection :datomic])))
  ([] (create-system {})))

