(ns catalysis.app
  "This is where all the application logic goes. If we need to, we can branch off subnamespaces. But unless there is
  any need for separation of state concerns, like try to keep all app stuff in here."
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [catalysis.datomic :as datomic]
            [catalysis.ws :as ws]))

(defrecord App [config ws-connection]
  component/Lifecycle
  (start [component]
    (log/debug "Application logic started")
    component)
  (stop [component]
    (log/debug "Application logic stopped")
    component))

(defn new-app []
  (map->App {}))


;; TODO Set up datsync hooks, other message handlers, etc

