(ns datview.comms
  (:require [com.stuartsierra.component :as component]
            [datview.utils :as utils]))


;; These protocols define the shape of an object Comms, which should satisfy PSendMessage and one of PMessageChannel or PMessageHandler,
;; and can be passed along in the construction of a Datview object as a thing which abstracts the role of communicating messages with server.
;; Eventually we may build around this and move this into Datsync, since it would be nice if it was in charge of all state/message management.
;; Reason on that is that we'd like to deal with non-tx messages/events transactionally as well.


;; ## Sending messages

(defprotocol PSendMessage
  (send-message! [this message]))


;; ## Receiving messages

;; Only one of these should be implemented to avoid confusion; Defaults to using PMessageChannel if implemented

(defprotocol PMessageChannel
  "Implement this on your Comms SS Component if you want to handle messages using a core.async channel (preferred for pipelining)."
  (message-chan [this]))

(defprotocol PMessageHandler
  "Implement this on your Comms SS Component if you want to handle messages directly."
  (handle-message! [this message]))


