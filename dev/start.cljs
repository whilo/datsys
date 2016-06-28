(ns dat.sys.start
  (:require [dat.sys.client.app :as app]
            [dat.view :as view]
            ))

(enable-console-print!)

(defn on-js-reload []
  (prn "------ Figwheel Has Reloaded ------")
  (do
    ;;(app/main)
    (view/dispatch! (:app app/system) [:figwheel/reload nil])
    )
  )

(app/main)
