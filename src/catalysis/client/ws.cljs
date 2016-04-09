(ns catalysis.client.ws
  (:require [taoensso.sente :as sente]
            [catalysis.client.db :as db]
            [datsync.client.core :as datsync]
            [taoensso.sente.packers.transit :as sente-transit]))


;; So we can send messages from handlers
(declare send-tx!)
(declare chsk-send!)

;; ## Top level event handlers

;; Dispatch on event-id
(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event)))


(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (:first-open? ?data)
    (do (js/console.log "Channel socket successfully established!")
        (js/console.log "Sending bootstrap request")
        (chsk-send! [:datsync.client/bootstrap nil]))
    (js/console.log "Channel socket state change: %s" (pr-str ?data))))

;; Set up push message handler

; Dispatch on event key which is 1st elem in vector
(defmulti push-msg-handler first)

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (push-msg-handler ?data))


;; ## Push message handlers

(defmethod push-msg-handler :datsync/tx-data
  [[_ tx-data]]
  (js/console.log "tx-data recieved")
  (datsync/apply-remote-tx! db/conn tx-data))

(defmethod push-msg-handler :datsync.client/bootstrap
  [[_ tx-data]]
  ;; Possibly flag some state somewhere saying bootstrap has taken place?
  (js/console.log "Recieved bootstrap")
  (datsync/apply-remote-tx! db/conn tx-data))

;; TODO Add any custom handlers here!




;; ## Set up Sente, define send-tx!, and hook up message handler router

;; This is a hack to get the db/fn objects to not break on data load
(defrecord DBFn [lang params code])
;(defn tagged-fn [:datsync.server/db-fn])
(cljs.reader/register-tag-parser! 'db/fn pr-str)

(let [packer (sente-transit/get-flexi-packer :edn)
      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto :packer packer})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn send-tx! [conn tx]
  (js/console.log "Sending tx to server")
  (chsk-send! [:datsync.client/tx (datsync/datomic-tx conn tx)])
  (js/console.log "Transaction sent"))


;; Hook up the message handler router
(sente/start-chsk-router! ch-chsk event-msg-handler)



