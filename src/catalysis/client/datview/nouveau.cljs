(ns catalysis.client.datview.nouveau
  "# Datview nouveau"
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [posh.core :as posh]
            [catalysis.client.router :as router]
            [catalysis.client.ws :as ws]
            [catalysis.shared.utils :as utils]
            [reagent.core :as r]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [datsync.client :as datsync]
            [posh.core :as posh]
            [datascript.core :as d]
            [goog.date.Date]
            [cljs-time.core :as cljs-time]
            [cljs-time.format]
            [cljs-time.coerce]
            [cljs.pprint :as pp]
            [cljs.core.match :as match :refer-macros [match]]
            #_[markdown.core :as md]))


;; # Note on this namespace!

;; This is really just a fork of the datview namepsace, which has been separated out to work on a smarter
;; approach to the performance issues associated with the initial implementation of datview.
;; One things are working here, we'll move it all over to datview and eventaully pull out as a library.



;; ## Overview

;; What if you could write views which compute themselves based on the data you pass in?

;; Datview is a set of tools for doing this, and offering utility functions for translating Datomic data into
;; hiccup, at anywhere from a level expected of a scaffolding system (without the code to maintain) to that of
;; a helper lib of useful pieces.
;; The goal of datview is to cover this all with a very data driven spec (specified through schema)


;; ## First some helpers

(defn as-reaction
  "Treat a regular atom as though it were a reaction"
  [vanilla-atom]
  (let [trigger (r/atom 0)]
    (add-watch vanilla-atom :as-reaction-trigger (fn [& args] (swap! trigger inc)))
    (reaction
      @trigger
      @vanilla-atom)))


;; Have to think about how styles should be separated from container structure, etc, and how things like
;; little control bars can be modularly extended, etc.
;; How can this be modularized enough to be truly generally useful?



(def my-hbox-defaults
  {:style {:flex-flow "row wrap"}})

(def my-vbox-defaults
  {:style {:flex-flow "column wrap"}})

;; Might be nice to override these things
;(defn vbox
  ;[& {:as kvs}]
  ;(->> kvs
       ;(u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       ;seq
       ;flatten
       ;(apply re-com/v-box)))

;(defn hbox
  ;[& {:as kvs}]
  ;(->> kvs
       ;(u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       ;(u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       ;seq
       ;flatten
       ;(apply re-com/h-box)))


(defn debug-str
  ([message data]
   (str message (debug-str data)))
  ([data]
   (with-out-str (pp/pprint data))))

(defn debug
  ([message data]
   [:div.debug 
    [:p message]
    [:pre (debug-str data)]])
  ([data]
   (debug "" data)))

;; Helpers

(defn collapse-button
  "A collapse button for hiding information; arg collapse? should be a bool or an ratom thereof.
  If no click handler is specified, toggles the atom."
  ([collapse? on-click-fn]
   (let [[icon-name tooltip] (if (try @collapse? (catch js/Object e collapse?)) ;; not positive this will work the way I expect
                               ["zmdi-caret-right" "Expand collection"]
                               ["zmdi-caret-down" "Hide collection"])]
     [re-com/md-icon-button :md-icon-name icon-name
                            :tooltip tooltip
                            :on-click on-click-fn]))
  ([collapse?]
   (collapse-button collapse? (fn [] (swap! collapse? not)))))


;; ## Datalog rules

;; Need to look more closely at which of these we actually need; We may not need many at all



;; ## Pull view

;; This is a new idea... originally, I was basing things on the entity-view component.
;; But I'm realizing this is bad for the performance story.
;; What we really need is a function that takes data from a pull, and renders all present relatonships.
;; This is I think the best way to minimize the amount of querying needed to retrieve data for some entity..


(declare pull-view)

(defn pull-view
  "Specify a db connection and a pull-data atom or value for which data is to be rendered. Three argument
  function call takes pull-pattern and eid and runs posh pull for you."
  ([conn pull-data] 
   (let [pull-data (utils/deref-or-value pull-data)])))


;; ## Entity views

;; This needs to be rewritten in terms of pull view (using *, I guess)

(declare entity-view)

(defn entity-name
  [entity]
  (match [entity]
    [{:e/name name}] name
    [{:e/type type}] (name type)
    [{:attribute/label label}] label
    ;; A terrible assumption really, but fine enough for now
    :else (pr-str entity)))

;; Mmm... this is probably really innefficient... should make this be based on the pull I think... Are we even
;; using it anywhere right now? Should just use entity-name above now...

