(ns catalysis.client.ws
  (:require [taoensso.sente :as sente]
            [datsync.client.core :as datsync]
            [taoensso.sente.packers.transit :as sente-transit]))


;; Dispatch on event-id
(defmulti event-msg-handler :id)

;; Wrap for logging, catching, etc:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

;; Should put in component XXX
(let [packer (sente-transit/get-flexi-packer :edn)
      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto :packer packer})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn send-tx! [conn tx]
  (chsk-send! [:datsync.client/tx (datsync/datomic-tx conn tx)]))

(sente/start-chsk-router! ch-chsk event-msg-handler*)


