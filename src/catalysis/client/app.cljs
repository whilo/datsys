(ns catalysis.client.app
    (:require-macros [cljs.core.async.macros :refer [go-loop]]
                     [reagent.ratom :refer [reaction]])
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [catalysis.client.views :as views]
              [catalysis.client.ws :as ws]
              ))

;; TODO Set up datascript conn, datsync and posh

(defonce state
  (reagent/atom {:title "Catalyst"
                 :messages []
                 :re-render-flip false}))

;; Setting up data
(ws/chsk-send! [:catalysis/load-base-data {}])

(def tags (reaction {:tags-state (last (filter :tags-state (:messages @state)))}))

(defmulti handle-event (fn [data [ev-id ev-data]] ev-id))

(defmethod handle-event :default
  [data [_ msg]]
  (swap! data update-in [:messages] #(conj % msg)))

;; TODO Handle all events from the socket here by extending this multimethod

(defn app [data]
  (:re-render-flip @data)
  [views/main data])

(defn ^:export main []
  (when-let [root (.getElementById js/document "app")]
    (reagent/render-component [app state] root)))

