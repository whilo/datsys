(ns datview.comms
  (:require [com.stuartsierra.component :as component]
            [datview.utils :as utils]))


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


