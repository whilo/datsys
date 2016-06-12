(ns dat.sys.client.events
  (:require [dat.reactor :as reactor]))


;; This is where you would handle custom message ids, perhaps something like this

;;     (defmethod reactor/handle-message! :your-special-message-id
;;       [app [id data]]
;;       (js/console.log "Data is:" data)


