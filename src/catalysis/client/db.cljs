(ns catalysis.client.db
  (:require [datsync.client.core :as datsync]
            [datascript.core :as d]
            [posh.core :as posh]))


;; Set up our database and initialize posh

(defonce conn (d/create-conn datsync/base-schema))

(posh/posh! conn)

;; crickets; seems like we're using nss as ss component
;; kind of anti pattern; maybe it will make more sense if we also
;; put our posh reactions/subscriptions here



