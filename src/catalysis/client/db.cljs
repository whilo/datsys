(ns catalysis.client.db
  (:require [datsync.client :as datsync]
            [datascript.core :as d]
            [catalysis.client.datview.nouveau :as datview]
            [catalysis.shared.utils :as utils]
            [posh.core :as posh]))


;; Set up our database and initialize posh

(defonce conn
  (d/create-conn (utils/deep-merge datsync/base-schema
                                   datview/base-schema)))

(d/transact! conn datview/default-settings)

(posh/posh! conn)

;; crickets; this is mostly just state management right now, which is icky; kind of anti-pattern
;; may make more sense to keep subscriptions or posh functions here



