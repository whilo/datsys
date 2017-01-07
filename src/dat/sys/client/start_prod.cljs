(ns dat.sys.client.start-prod
  (:require [dat.sys.client.app :as app]
            [dat.view :as view]
            [taoensso.timbre :as log :include-macros true]
            ))

(defn on-js-reload []
  (log/info "------ Reload ------")
  (do
    (app/main)))

(log/info "------ Starting ------")
(app/main)

