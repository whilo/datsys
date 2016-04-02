(ns catalysis.client.app
    (:require-macros [cljs.core.async.macros :refer [go-loop]]
                     [reagent.ratom :refer [reaction]])
    (:require [reagent.core :as reagent]
              [posh.core :as posh :refer [pull q db-tx pull-tx q-tx after-tx! transact! posh!]]
              [datsync.client.core :as datsync]
              [datascript.core :as d]
              [catalysis.client.views :as views]
              [catalysis.client.db :as db]
              [catalysis.client.ws :as ws]))
              

(defn ^:export main []
  (when-let [root (.getElementById js/document "app")]
    (reagent/render-component [views/main db/conn] root)))

