(ns dat.sys.system
  "System constructor."
  (:require [com.stuartsierra.component :as component]
            [dat.sys.ws :as ws]
            [dat.sys.config :as config]
            [dat.sys.datomic :as datomic]
            [dat.sys.server :as server]
            [dat.sys.app :as app]
            [dat.sys.import :as import]))
            

(defn create-system
  ([config-overrides]
   (component/system-map
     :config (config/create-config config-overrides)
     :datomic (component/using (datomic/create-datomic) [:config])
     :importer (component/using (import/new-importer) [:config :datomic])
     :ws-connection (component/using (ws/new-ws-connection) [:config])
     :http-server (component/using (server/new-http-server) [:datomic :config :ws-connection])
     :app (component/using (app/new-app) [:config :ws-connection :datomic])))
  ([] (create-system {})))

