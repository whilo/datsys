(ns catalysis.client.views
  "# Views"
  (:require [catalysis.client.ws :as ws]
            [catalysis.datview :as datview]
            [posh.core :as posh]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame] 
            [clojure.string :as string]
            [datascript.core :as d]
            [cljs.reader :as reader]))


;; ## The core views namespace...

;; At present, this template (not yet in the Leiningen sense...) starts you off with a simple todo app to see
;; how data flows through the system and how all the various peices fit together



;; ## Reactive queries (subscriptions)

;; We define the flow of data through our application via posh.
;; Posh is: Reactive materialized views (in the database sense, not the MVC sense) as DataScript queries and
;; db modifiers such as `q`, `pull`, (and coming soon `filter` and `with`).
;; (For more info: <https://github.com/mpdairy/posh>).

;; Here we show some general purpose, high-level reactions which we'll be using in our app.

;; We define schema as anything that has an ident;
;; We can pass this around to more efficiently.

(defn schema-rx
  [conn [_]]
  (posh/q conn
          '[:find [(pull ?e [*]) ...]
            :where [?e :db/ident]]))

;; Not sure if it's permissable for subscription functions not to take args.
;; Or whether we really even want to use subscription functions...
;; I do like the indirection.
;; And it's possible it will help with issues like these where we want to keep around these reactive queries.

;(re-frame/register-sub :schema schema-rx)

(defn type-instances-rx
  [conn [type-ident]]
  (posh/q conn
          '[:find [(pull ?e [*]) ...]
            :in $ ?type-ident
            :where [?e :e/type ?type-ident]]
          type-ident))

(defn todos-rx
  [conn]
  (type-instances-rx conn [:e.type/Todo]))

;(re-frame/register-sub :type-instances type-instances-rx)


;; We may have to build our own notion of subscriptions... I'm not seeing the re-frame subscriptions working
;; well, because they defined based on a predefined var :-(
;; This should really be modular; or there should be an init fn.
;; Still bad for moving towards SS Component or something similar for state management.


;; ## Views (in the MVC sense)

;; These could easily be extracted into proper reagent subscriptions

(defn todos-view [conn]
  [re-com/v-box
   :children (for [todo @(todos-rx conn)]
               ;; XXX This is rather stupid; we need to entity-view to take a full pull, not just the eid.
               ;; This should make things a lot more performant; see the relevant code in datview
               ^{:key (:db/id todo)}
               [datview/entity-view-with-controls conn (:db/id todo)])])

(defn main [conn]
  (println "Rendering main function")
  [re-com/v-box
   :gap "15px"
   :children [[:h1 "Catalysis"]
              [:p "Congrats! You've got a catalysis app running :-)"]
              [todos-view conn]]])



