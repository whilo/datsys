(ns catalysis.client.app
    (:require-macros [cljs.core.async.macros :refer [go-loop]]
                     [reagent.ratom :refer [reaction]])
    (:require [reagent.core :as reagent]
              [posh.core :as posh]
              [datsync.client.core :as datsync]
              [datascript.core :as d]
              [catalysis.client.views :as views]
              [catalysis.client.db :as db]
              [catalysis.client.ws :as ws]
              [catalysis.client.handlers :as handlers]))


;; Just hooking up view to conn

(defn ^:export main []
  (when-let [root (.getElementById js/document "app")]
    (reagent/render-component [views/main db/conn] root)))

