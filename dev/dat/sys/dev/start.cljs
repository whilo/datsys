(ns dat.sys.dev.start
  (:require [dat.sys.client.app :as app]
            [dat.view :as view]
            [taoensso.timbre :as log :include-macros true]
            ))

(enable-console-print!)

(defn on-js-reload []
  (log/info "------ Figwheel Has Reloaded ------")
  (do
    ;;(app/main)
    (view/dispatch! (:app app/system) [:figwheel/reload nil])
    )
  )

(app/main)
