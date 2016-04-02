(ns catalysis.client.handlers
  (:require [datsync.client.core :as datsync]
            [catalysis.client.ws :as ws]
            [catalysis.client.db :as db]))


;; ## Top level event handlers

(defmethod ws/event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event)))

(defn bootstrap [conn]
  (js/console.log "starting bootstrap")
  (ws/chsk-send!
    [:datsync.client/bootstrap nil]
    4000
    (fn [tx]
      (do (js/console.log "running callback")
      (datsync/apply-remote-tx! conn tx)))))

(defmethod ws/event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (:first-open? ?data)
;  (do
    (js/console.log "Channel socket successfully established!")
;       (bootstrap db/conn))
    (js/console.log "Channel socket state change: %s" (pr-str ?data))))

(defmethod ws/event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (bootstrap db/conn)
  )
;; Set up push message handler

; Dispatch on event key which is 1st elem in vector
(defmulti push-msg-handler
  (fn [[id _]] id))

(defmethod ws/event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (push-msg-handler ?data))



;; ## Push message handlers

(defmethod push-msg-handler :datsync/tx-data
  [[_ tx-data]]
  (datsync/apply-remote-tx! db/conn tx-data))




