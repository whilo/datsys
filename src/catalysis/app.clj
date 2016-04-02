(ns catalysis.app
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [catalysis.datomic :as datomic]
            [catalysis.ws :as ws]
            [taoensso.sente :as sente]
            [datsync.server.core :as datsync]
            [datomic.api :as d]))


;; # Application

;; This namespace contains the core of the server's message handling logic.
;; Any custom message handlers on the server you want to hook up should go here


;; ## First we set up our event handler multimethod function, and a wrapper for it

; Dispatch on event-id
(defmulti event-msg-handler :id)

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [app {:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg app))



;; ## Transaction report handler

(defn handle-transaction-report!
  [ws-connection tx-deltas]
  ;; This handler is where you would eventually set up subscriptions
  (try
    (ws/broadcast! ws-connection [:datsync/tx-data tx-deltas])
    (catch Exception e
      (log/error "Failed to send transaction report to clients!")
      (.printStackTrace e))))


;; ## The actual app component, which starts sente's chsk router and hooks up the msg handler

(defrecord App [config datomic ws-connection sente-stop-fn]
  component/Lifecycle
  (start [component]
    (log/info "Starting websocket router and transaction listener")
    (let [sente-stop-fn (sente/start-chsk-router!
                          (:ch-recv ws-connection)
                          (partial event-msg-handler component))]
      ;; Start our transaction listener
      (datsync/start-transaction-listener! (:tx-report-queue datomic) (partial handle-transaction-report! ws-connection))
      (assoc component :sente-stop-fn sente-stop-fn)))
  (stop [component]
    (log/debug "Stopping websocket router")
    (sente-stop-fn)
    component))

(defn new-app []
  (map->App {}))

(defrecord App [config ws-connection]
  component/Lifecycle
  (start [component]
    (log/debug "Application logic started")
    component)
  (stop [component]
    (log/debug "Application logic stopped")
    component))

(defn new-app []
  (map->App {}))


;; ## Event handlers

;; don't really need this... should delete
(defmethod event-msg-handler :chsk/ws-ping
  [_ _])

;; Setting up our two main datsync hooks

(defmethod event-msg-handler :datsync.client/tx
  [{:as app :keys [datomic]} {:as ev-msg :keys [id ?data]}]
  (let [tx-report @(datsync/transact-from-client! (:conn datomic) ?data)]
    (println "Do something with:" tx-report)))

(defmethod event-msg-handler :datsync.client/bootstrap
  [{:as app :keys [datomic]} {:as ev-msg :keys [id ?data ?reply-fn send-fn]}]
  (when ?reply-fn
    (log/info "Sending bootstrap message")
    (?reply-fn (datomic/bootstrap (d/db (:conn datomic))))))

;; Fallback handler; send message saying I don't know what you mean

(defmethod event-msg-handler :default ; Fallback
  [app {:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/warn "Unhandled event:" (with-out-str (clojure.pprint/pprint event))))


