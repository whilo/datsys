(ns catalysis.start
  (:require [figwheel.client :as fw]
            [catalysis.client.db :as db]
            [catalysis.client.app :as app]))

(enable-console-print!)

(fw/watch-and-reload
 :websocket-url "ws://localhost:3448/figwheel-ws")
 ;:jsload-callback #(swap! app/state update-in [:re-render-flip] not))

(app/main)
