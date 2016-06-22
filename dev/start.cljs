(ns dat.sys.start
  (:require [figwheel.client :as fw]
            [dat.sys.client.app :as app]
            [dat.view]))

(enable-console-print!)

(fw/watch-and-reload
 :websocket-url "ws://localhost:3448/figwheel-ws"
 :jsload-callback #(do (app/main)
                       (dat.view/dispatch! (:app app/system) [:figwheel/reload nil])))

(app/main)

