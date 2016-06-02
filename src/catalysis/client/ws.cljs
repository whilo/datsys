(ns catalysis.client.ws
  (:require [taoensso.sente :as sente]
            [catalysis.client.db :as db]
            [datsync.client :as datsync]
            [datview.comms :as comms]
            [cljs.core.async :as async]
            [taoensso.sente.packers.transit :as sente-transit]))




;; ## Set up Sente, define send-tx!, and hook up message handler router

;; This is a hack to get the db/fn objects to not break on data load
(defrecord DBFn [lang params code])
;(defn tagged-fn [:datsync.server/db-fn])
(cljs.reader/register-tag-parser! 'db/fn pr-str)

(defrecord WSConnection [chsk ch-recv send-fn state open?]
  component/Lifecycle
  (start [component]
    (let [packer (sente-transit/get-flexi-packer :edn)
          sente-fns (sente/make-channel-socket! "/chsk" {:type :auto :packer packer})]
      ;; Don't know that we want to stay with this; We may want to build around transducer processing instructions
      (sente/start-chsk-router! (:ch-recv sente-fns) event-msg-handler)
      (merge component
             sente-fns
             {:open? (atom false)})))
  (stop [component]
    (when ch-recv (async/close! ch-recv))
    (js/console.log "WebSocket connection stopped")
    component)
  comms/PSendMessage
  (send-message! [component message]
    (send-fn message))
  comms/PMessageChannel
  (message-chan [component]
    ch-recv))

(defn new-ws-connection []
  (map->WSConnection {}))


