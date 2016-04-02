(ns catalysis.ws
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [clojure.core.async :as async]
            [datsync.server.core :as datsync]
            [taoensso.sente.server-adapters.http-kit :as sente-http]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]))

(def ping-counts (atom 0))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [datomic {:as ev-msg :keys [id ?data event]}]
  ;(log/info "Just got event:" event)
  ;(log/info "Just got data" ?data)
  (event-msg-handler ev-msg datomic))

(defmethod event-msg-handler :chsk/ws-ping
  [_ _]
  (swap! ping-counts inc)
  (when (= 0 (mod @ping-counts 10))
    (println "ping counts: " @ping-counts)))

(defmethod event-msg-handler :datsync.client/tx
  [{:as ev-msg :keys [id ?data]} datomic]
  (let [tx-report @(datsync/transact-from-client! datomic ?data)]
    (println "Do something with:" tx-report)))

(defmethod event-msg-handler :datsync.client/bootstrap
  [{:as ev-msg :keys [id ?data ?reply-fn send-fn]} datomic]
  (if ?reply-fn
    ;(?reply-fn [:datsync/tx (for [datom (d/datoms (d/db (:conn datomic)) :eavt)] (let [[e a v t] datom] [:db/add e a v]))]))
    (?reply-fn
      (let [eids (map (fn [[e a v t]] e) (d/datoms (d/db (:conn datomic)) :eavt))]
        (println (d/pull-many (d/db (:conn datomic)) '[*] eids))
        (d/pull-many (d/db (:conn datomic)) '[*] eids)))
    ))



;; TODO Delete me and other "test" things once we get datsync in place
(defmethod event-msg-handler :catalysis/testevent
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]} _]
  (if ?reply-fn
    (?reply-fn [:catalysis/testevent {:message (str "Hello socket from server Callback, received: " ?data)}])
    (send-fn :sente/all-users-without-uid [:catalysis/testevent {:message (str "Hello socket from server Event (no callback), received: " ?data)}])))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]} _]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers datomic]
  component/Lifecycle
  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component
      (let [component (component/stop component)

            packer (sente-transit/get-flexi-packer :edn)

            {:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket! sente-http/http-kit-adapter {:packer packer})]
        (log/info "WebSocket connection started")
        (assoc component
          :ch-recv ch-recv
          :connected-uids connected-uids
          :send-fn send-fn
          :stop-the-thing (sente/start-chsk-router! ch-recv (partial event-msg-handler* datomic))
          :ring-handlers
          (->WSRingHandlers ajax-post-fn ajax-get-or-ws-handshake-fn)))))
  (stop [component]
    (when ch-recv (async/close! ch-recv))
    (log/debug "WebSocket connection stopped")
    (:stop-the-thing component)
    (assoc component
      :ch-recv nil :connected-uids nil :send-fn nil :ring-handlers nil)))

(defn send! [ws-connection user-id event]
  ((:send-fn ws-connection) user-id event))

(defn broadcast! [ws-connection event]
  (let [uids (ws-connection :connected-uids )]
    (doseq [uid (:any @uids)] (send! ws-connection uid event))))

(defn handle-transaction-report!
  [tx-deltas ws-connection]
  ;; This handler is where you would eventually set up subscriptions
  (try
    (broadcast! ws-connection [:datsync/tx-data tx-deltas])
    (catch Exception e
      (log/error "Failed to send transaction report to clients!")
      (.printStackTrace e))))

(defn ring-handlers [ws-connection]
  (:ring-handlers ws-connection))

(defn new-ws-connection []
  (map->WSConnection {}))


