(ns catalysis.client.db
  (:require [datsync.client.core :as datsync]
            [datascript.core :as d]
            [posh.core :as posh]))


;; Set up our database and initialize posh

(defonce conn (d/create-conn datsync/base-schema))

(posh/posh! conn)

;; crickets; this is mostly just state management right now, which is icky; kind of anti-pattern
;; may make more sense to keep subscriptions or posh functions here



