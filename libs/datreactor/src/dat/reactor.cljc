(ns dat.reactor
  #?(:cljs (:require-macros [cljs.core.async.macros :as async-macros :refer [go go-loop]]))
  (:require #?@(:clj [[clojure.core.async :as async :refer [go go-loop]]]
                :cljs [[cljs.core.async :as async]])
            #?(:clj [clojure.tools.logging :as log])
            [dat.spec.protocols :as protocols]
            [dat.reactor.dispatcher :as dispatcher]
            [datascript.core :as d]
            [com.stuartsierra.component :as component]))

;; This part of the application is still a rough work in progress
;; The overall idea is that we have a Re-frame style event processor, into which we pipe all of our websocket events
;; We also have a dispatch! function which is part of the public API.
;; Events from these and other potential event sources are sent into a core.async mix and processed in sequence.

;; Tihs is effectively the implementation of the reactor; The following reducer fn we extend with multimethods
(defmulti handle-event!
  (fn [_ _ [event-id _]] event-id))

;; # Reactor specific API

;; Wrap our dispatchers dispatch fns, for convenience

(defn dispatch!
  ([reactor message level]
   (dispatcher/dispatch! (:dispatcher reactor) message level))
  ([reactor message]
   (dispatcher/dispatch! (:dispatcher reactor) message)))

(defn dispatch-error!
  ([reactor message]
   (dispatcher/dispatch-error! (:dispatcher reactor) message)))

;; Next we're going to define the following function, which you can use in you own custom reactors to initiate
;; the recuction/transaction process (call in your component start implementation).

;; Be careful with this to avoid infinite loops
(defn resolve-to
  "Within a handle-event method implementation, you can call this function to resolve a single event to
  some sequence of more atomic (presumably; thought you could get recursive...) events. This is a little bit
  experimental at the moment, as there might be some gotchas with error handling flow. But for right now, you
  have the option of specifying :datview.resolver/catch?, which lets you decide whether events should continue
  getting processed if one of the events errors (if set to truthy, skips over the errored event). Note that
  presently, errors do not bubble up. The last successful state of the db will be returned. Errors will be passed
  through to the :datview/error."
  ([app db events {:as options :keys [datview.resolver/catch?]}]
   (reduce
     (fn [db' event]
       ;; Handles all the expanded events and at the very end ends up transacting the final state
       ;; DB state is atomic under resolution, so you won't get any weird partial states affecting the UI by
       ;; doing resolution expansion
       (try
         (handle-event! app db' event)
         (catch #?(:clj Exception :cljs :default) e
           ;; Be warry of unhalting recurs here:
           (dispatcher/dispatch-error! app [:datview.resolver/error {:datview.error/event event :datview.js/error e}])
           (if catch?
             db'
             ;; Should we try merging in the error as metadata? Am I crazy like a fox?
             (with-meta (reduced db') (merge (meta db') {:datview.resolver/error e}))))))
     db
     ;; So we can do (when ...) in our blocks
     (remove nil? events)))
   ;; This should raise, but doesn't
  ([app db events]
   (resolve-to app db events {})))




;; TODO Should be able to "register" error subtypes/idents for general purpose error handling


;; FWIW; I think we need a separate notion of effects in this framework.
;; But in general there needs to be a lot of careful thought about how we synchronize state and side effects.


;; This thing should be state local to the datsync component, for the sake of running multiple systems,
;; methinks. Just don't want to have to bother writing the abstractions right now. Could use help on this TODO

;; Woah; Specter based dispatch system?
;; Specify a path to the thing you want to dispatch off of.
;; Can build middleware around things to lense/indirect that for plugins.
;; For now, hard coded as positional vector.

(defn preserve-meta
  [handler-fn]
  (fn [app db event]
    (if (meta db)
      db
      (with-meta db (meta db)))))

(defn register-handler
  "Register an event handler. Optionally specify middleware as second arg. Can be a vector of such fns, as well.
  Middleware is typical in order; First in the sequence ends up being responsible for creating the handler function
  that actually returns the final value. Except... We have some default handlers (see implementation, for now, till
  we spec this out)."
  ([event-id handler-fn]
   (register-handler event-id [] handler-fn))
  ;; Should also look at the arity of the fn to decide whether or not to pass through app; But always passes
  ;; through purely and statically from above; No reducing...
  ([event-id middleware-fn handler-fn]
   (let [post-middleware [preserve-meta] ;; make sure we pass through original metadata if anything weird happens to it
         pre-middleware [] ;; nothing for now
         middleware-fns (vec (concat post-middleware
                                     (if (sequential? middleware-fn) middleware-fn [middleware-fn])
                                     pre-middleware))
         middleware-fn (apply comp middleware-fns)
         handler-fn (middleware-fn handler-fn)]
     (defmethod handle-event! event-id
       [app db event] (handler-fn app db event)))))

(defn register-handlers [reactor handler-specs]
  "Takes a vector of vectors of shape [event-id handler-fn] or [event-id middleware-fn handler-fn]
  and calls `register-handler` on them."
  (doseq [handler-spec handler-specs]
    (apply register-handler handler-spec)))



;; The current implementation of effects is only gonna support firing effects based on the db at the end of a
;; transaction (commit).

(defmulti execute-effect!
  (fn [app db [effect-id effect-data]]
    effect-id))

(def concatv
  (comp vec concat))

(defn with-effects
  "Registers effects on the database value. This is the mode of communication for effect message which need to get processed."
  [effects db]
  (with-meta
    db
    (update (meta db)
            ::effects
            concatv
            ;; This should hopefully let us run effects on the db values from whence they triggered
            (map (fn [effect] (with-meta effects {:db db}))
                 effects))))

(defn with-effect
  "Registers effect on the database value. This is the mode of communication for effect message which need to get processed."
  [effect db]
  (with-effects [effect] db))


;; Would be nice if registering an effect also created an event handler by the same id that just triggers that
;; effect... (This happens now, but see caveat in definition below)
;; Should also have errors in effect handlers logged and handled. TODO
;; Need to think about effect handlers in general as well. TODO
;; Should we have doc strings for register functions? That would be nice... TODO
(defn register-effect
  "Register an event handler. Optionally specify middleware as second arg. Can be a vector of such fns, as well.
  Middleware is typical in order; First in the sequence ends up being responsible for creating the handler function
  that actually returns the final value. Except... We have some default handlers (see implementation, for now, till
  we spec this out). Also, calling this function registers an _event_ handler by the same effect-id; This should
  eventually act as a default, but not override any event handler already set up with the same id, but for now avoid
  collisions between event and effect ids."
  ([effect-id effect-fn]
   (register-handler [] effect-fn))
  ;; Should also look at the arity of the fn to decide whether or not to pass through app; But always passes
  ;; through purely and statically from above; No reducing...
  ([effect-id middleware-fn effect-fn]
   (let [post-middleware [] ;; make sure we pass through original metadata if anything weird happens to it
         pre-middleware [] ;; nothing for now
         middleware-fns (vec (concat post-middleware
                                     (if (sequential? middleware-fn) middleware-fn [middleware-fn])
                                     pre-middleware))
         middleware-fn (apply comp middleware-fns)
         effect-fn (middleware-fn effect-fn)]
     (defmethod execute-effect! effect-id
       [app db effect] (effect-fn app db effect))
     ;; TODO Should try to only do this if there isn't already one set, so that this just behaves like a default...
     (defmethod handle-event! effect-id
       [app db effect]
       (with-effect effect db)))))



;; This is our handler registration system; We register handlers under event-id, and can optionally specify
;; middleware functions applied to the handler-fn.




(register-handler ::resolve-tx-report
  (fn [_ db [_ tx-report]]
    (:db-after tx-report)))

;; For compatibility with DataScript handlers...
(register-effect ::fire-tx-report-handlers!
  (fn [app db [_ tx-report]]
    ;; Hmm... would be nice to get hese from where they happen
    (doseq [[_ callback] @(:listeners (meta (:conn app)))]
      (callback tx-report))))
  

(register-handler ::local-tx
  (fn [app db [_ tx-forms]]
    (let [tx-report (d/with db tx-forms)]
      (with-effect
        [::execute-tx-report-handler tx-report]
        (resolve-to app db [[::resolve-tx-report tx-report]])))))

(register-effect ::console-log
  (fn [app db data]
    (#?(:cljs js/console.log :clj log/info) "Logging:" data)))


;; ## Component

;; Now for our actual component

(defn go-react!
  "Starts a go loop that processes events and effects using the handle-event! and
  execute-effect! fns. Effects are executed in sequence after the transaction commits.
  If a handler fails, the effects will not fire (will eventually support control over
  this behavior)."
  [reactor app]
  (go-loop []
    ;; Should probably use dispatcher api's version of event-chan here...
    (let [event (async/<! (protocols/dispatcher-event-chan (:dispatcher reactor)))
          final-meta (atom nil)
          conn (:conn reactor)]
      (swap!
        (:conn reactor)
        (fn [current-db]
          (try
            (let [new-db (handle-event! reactor current-db event)]
              (reset! final-meta (meta new-db))
              (with-meta new-db (dissoc ::effects (meta new-db))))
            ;; We might just want to have our own error channel here, and set an option in the reactor
            (catch #?(:clj Exception :cljs :default) e
              (dispatch-error! reactor [::error {:error e :event event}] :error)
              current-db))))
      (when-let [effects (seq (::effects @final-meta))]
        (doseq [effect effects]
          ;; Not sure if the db will pass through properly here so that effects execute on the db values
          ;; immediately following their execution trigger
          (execute-effect! app (or (:db (meta effect)) @conn) effect))))))

(defrecord SimpleReactor [app dispatcher conn]
  component/Lifecycle
  (start [reactor]
    (go-react! reactor app)
    (assoc reactor
           :conn (or conn (:conn app) (d/create-conn))))
  (stop [reactor]
    reactor))

(defn new-simple-reactor
  ([options]
   (map->SimpleReactor options))
  ([]
   (new-simple-reactor {})))


