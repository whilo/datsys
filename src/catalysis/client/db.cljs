(ns catalysis.client.db
  (:require [datsync.client :as datsync]
            [com.stuartsierra.component :as component]
            [datascript.core :as d]
            [datview.core :as datview]
            [catalysis.shared.utils :as utils]
            [posh.core :as posh]))


;; Set up our database and initialize posh

(defrecord DSConn [conn history] ;; Eventually do history
  component/Lifecycle
  (start [component]
    (js/console.log "Starting DSComponent")
    (let [conn (or conn (d/create-conn datsync/base-schema))] 
      (d/transact! conn datview/default-settings)
      (posh/posh! conn) ;; Not sure if this is the best place for this
      (assoc component :conn conn))) 
  (stop [component]
    ;; No op; not sure what I might do here
    component))


(defn new-ds-conn []
  (map->DSConn {}))


