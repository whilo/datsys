(ns catalysis.client.app
    (:require-macros [cljs.core.async.macros :refer [go-loop]]
                     [reagent.ratom :refer [reaction]])
    (:require [reagent.core :as reagent]
              [posh.core :refer [pull q db-tx pull-tx q-tx after-tx! transact! posh!]]
              [datsync.client.core :as datsync]
              [datascript.core :as d]
              [catalysis.client.views :as views]
              [catalysis.client.ws :as ws]
              ))

;; TODO Set up datascript conn, datsync and posh

(defonce state
  (reagent/atom {:title "Catalyst"
                 :messages []
                 :re-render-flip false}))

(ws/test-socket-callback)

(def conn (d/create-conn datsync/base-schema))

(posh! conn)

;; Setting up data
;(ws/chsk-send! [:catalysis/load-base-data {()}])

;(def tags (reagent/reaction {:tags-state (last (filter :tags-state (:messages @state)))}))

;; TODO Handle all events from the socket here by extending this multimethod

(defn app [data]
  ;(:re-render-flip @data)
  [views/main data])

(defn ^:export main []
  (when-let [root (.getElementById js/document "app")]
    (reagent/render-component [app state] root)))

