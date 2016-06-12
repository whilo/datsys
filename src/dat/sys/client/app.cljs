(ns dat.sys.client.app
    (:require-macros [cljs.core.async.macros :refer [go-loop]]
                     [reagent.ratom :refer [reaction]])
    (:require [dat.view :as view]
              [dat.reactor]
              [dat.remote]
              [dat.remote.impl.sente :as sente]
              [dat.sync.client :as dat.sync]
              [dat.sys.client.views]
              [dat.sys.client.events]
              [dat.reactor.dispatcher]
              [reagent.core :as reagent]
              [com.stuartsierra.component :as component]
              [posh.core :as posh]
              [datascript.core :as d]))


;; # The system & main function

;; This is where everything actually ties together and starts.
;; If you're interested in tweaking things at a system level, have a look at metasoarous/datprotocols


;; ## The simple default system

;; Beep-beedeelee-beep, that's all folks!

(defn new-system []
  (-> (component/system-map
        :remote     (sente/new-sente-remote)
        :dispatcher (dispatcher/new-strictly-ordered-dispatcher)
        :app        (component/using
                      (view/new-datview {:dat.view/main views/main})
                      [:remote :dispatcher])
        :reactor    (component/using
                      (dat.sync/new-datsync-reactor)
                      [:remote :dispatcher :app]))))


;; ## Customizing things

;; That's all fine and dandy, but supposing we want to customize things?
;; This is a more fleshed out example of the system components being strung together

;;     (defn new-system []
;;       (-> (component/system-map
;;             ;; Have to require dat.reactor.dispatchers for this:
;;             :dispatcher (dispatchers/new-strictly-ordered-dispatcher)
;;             :remote     (dat.sync/new-sente-remote)
;;             :reactor    (component/using (dat.sync/new-datsync-reactor) [:remote :dispatcher])
;;             :app        (component/using (view/new-datview {:dat.view/main views/main} [:remote :reactor :dispatcher]))))

;; If we don't specify :dispatcher or :remote, they get plugged in automatically by the datsync reactor, and
;; get plugged into datview for use in its components as well.


;; ## Stripping things down

;; Oh... You're not using DatSync but still wanna use DatView?

;; No problem.
;; Just plug in your own reactor.
;; As long as it satsfies the reactor protocols, everything should just work.
;; As long as our abstractions aren't leaking for you...
;; (Tell us if they do...)

;; Here's a quick example of what some 

;;     (defn new-system []
;;       (-> (component/system-map
;;             :reactor    (reactor/new-simple-reactor)
;;             :load-data  (component/using (your.ns/new-data-loader) [:reactor])
;;             :app        (component/using (view/new-datview {:dat.view/main views/main} [:reactor :load-data]))))


;; ## Dev system

;; Note that you could also put your own dev system here, or a cards system, so that you can share the
;; important structure and swap in things like fighweel components, etc.


;; # Your apps main function

;; Just start the component!

(defn ^:export main []
  (component/start (new-system)))

