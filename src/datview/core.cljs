(ns datview.core
  "# Datview"
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [posh.core :as posh]
            [schema.core :as s
             :include-macros true]
            [catalysis.client.router :as router]
            [catalysis.shared.utils :as utils]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
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

(enable-console-print!)


;; ## Defaults

(def default-config
  ;; Not sure if this memoize will do what I'm hoping it does (:staying-alive true, effectively)
  (memoize
    (fn [conn]
      ;; Hmm... should we just serialize the structure fully?
      ;; Adds complexity around wanting to have namespaced attribute names for everything
      (reaction (:datview.default-config/value @(posh/pull conn '[*] [:db/ident :datview/default-config]))))))

(defn update-default-config!
  [conn f & args]
  (letfn [(txf [db]
            (apply update
                   (d/pull db '[*] [:db/ident :datview/default-config])
                   :datview.default-config/value
                   f
                   args))]
    (d/transact! conn [[:db.fn/call txf]])))

(defn set-default-config!
  [conn config]
  (update-default-config! conn (constantly config)))



;; ## Some helpers

(defn as-reaction
  "Treat a regular atom as though it were a reaction"
  [vanilla-atom]
  (let [trigger (r/atom 0)]
    (add-watch vanilla-atom :as-reaction-trigger (fn [& args] (swap! trigger inc)))
    (reaction
      @trigger
      @vanilla-atom)))

(defn pull-many-rx
  [conn pattern eids]
  (let [conn-reaction (as-reaction conn)]
    (reaction (d/pull-many @conn-reaction pattern eids))))


;; ## Base schema

;; Some basic schema that needs to be transacted into the database in order for these functions to work

(def base-schema
  {:datview.default-config/value {}})

(def default-settings
  [{:db/ident :datview/default-config
    :datview.default-config/value {}}])


;; Have to think about how styles should be separated from container structure, etc, and how things like
;; little control bars can be modularly extended, etc.
;; How can this be modularized enough to be truly generally useful?

;; These should be moved into styles ns or something

(def ^:dynamic box-styles
  {:display "inline-flex"
   :flex-wrap "wrap"})

(def ^:dynamic h-box-styles
  (merge box-styles
         {:flex-direction "row"}))

(def ^:dynamic v-box-styles
  (merge box-styles
         {:flex-direction "column"}))

(defn box
  "Prefers children over child"
  [{:as args :keys [style children child]}]
  [:div {:style (merge box-styles style)}
   ;; Not sure yet if this will work as expected
   (or (seq children) child)])

;; For debugging

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


;; ## Client Helper components

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


;; ## Builder pieces

;; These are builder pieces part of the public api;
;; These should be accessible for wrapping, and should be overridable/extensible via correspondingly named keys of the context map at various entry points

(defn entity-name
  [entity]
  (match [entity]
    [{:e/name name}] name
    [{:e/type type}] (name type)
    [{:attribute/label label}] label
    ;; A terrible assumption really, but fine enough for now
    :else (pr-str entity)))


;; ## Datalog rules

;; Need to look more closely at which of these we actually need; We may not need many at all

(def datalog-rules
  [])



;; ## Event handler

;; Need an even handler which can dispatch on some transaction patterns, and execute various messages or side effects.
;; I think posh may give this to us?
;; Or did in an old version?


;; ## Datview schema spec


;; ## Import

;; This is a great ingestion format
;; Make it possible to build semantic parsers for data on top of other web pages :-)




;; ## Data structures

;; ### Meta-types?

;; Use metadata to add structure to the schema definitions

(def StyleValue
  (s/cond-pre s/Str s/Num))

(def Style
  "Style idents map to style values."
  {s/Keyword
   StyleValue})

;; Should have a more general notion of how to construct functions between concepts
;; Like this should be able to take a style function...
(def ComponentStyleMapping
  {s/Keyword Style})

; Not sure I need this...
(defn ratomish?
  [x]
  (satisfies? ratom/IReactiveAtom x))

(def RAtom
  (s/protocol ratom/IReactiveAtom))

;; Function signature


(declare DomNode)
(declare GenericRenderFn)
(declare Hiccup)

(def DomNode
  (s/cond-pre s/Keyword #'GenericRenderFn))

(def HiccupAttrs
  ;; Just to s/Any for now
  {(s/optional-key :style) Style
   s/Keyword s/Any})

(def Hiccup
  ;; Nil is valid hiccup (renders as nothing)
  (s/maybe
    ;; First element is a dom node
    [(s/one DomNode "dom-node")
     ;; Can optionally have an attributes map
     (s/optional (s/cond-pre (s/recursive #'Hiccup) HiccupAttrs) "hiccup-attrs-or-child")
     ;; The rest is hiccup children
     (s/recursive #'Hiccup)]))


(def ViewDescription
  "Stub; The structure of a view description. This in general will either by a pull structure or a :find clause,
  but should be robust enough to represent any materialized view one might get from a reaction."
  s/Any)

(s/defn MaybeRatomOf :- s/Schema
  "Creates a schema which represents an input that either satisfies s, or is a RAtom of s. Note that s should not
  overlap with the definition of datview.schema/RAtom."
  [s :- s/Schema]
  ;; For static here need to push through that we want the ratom to resolve to this type; Could we extend the protocol function?
  (s/cond-pre RAtom s))

;; For now; Should show as derefable
(def DSConn s/Any)

;; Can't get this to work
;(s/defn ComponentRenderFnOf :- s/Schema
  ;"Render function signature for data of shape s. Note that s is restricted to the semantics of MaybeRatomOf."
  ;[for-type :- s/Schema]
  ;(s/=>* Hiccup
    ;[DSConn ViewDescription (MaybeRatomOf for-type)]))
    ;[DSConn ViewDescription s/Any]))

(declare PullExpr)

(def PullReferenceAttrExpr
  {s/Keyword (s/recursive #'PullExpr)})

(def RestSymbol '*)

(def PullAttrExpr
  (s/cond-pre s/Keyword PullReferenceAttrExpr RestSymbol))

(def PullExpr
  [PullAttrExpr])


;; Can't get the ComponentRenderFnOf dynamic schema to work
;(def GenericRenderFn
  ;(ComponentRenderFnOf s/Any))
;; This should maybe be something more specific
(def GenericRenderFn s/Any)

(def DataScriptDB s/Any)
(def WrapperFn s/Any)

(def NodeKeword s/Keyword)

(def StrictComponentAttrs
  {(s/optional-key :style) Style
   (s/optional-key :class) s/Str
   (s/optional-key :id) s/Str})

(def ComponentAttrs
  (merge
    StrictComponentAttrs
    {(s/optional-key :component) GenericRenderFn
     (s/optional-key :wrapper) WrapperFn
     s/Keyword s/Any}))

(def DatviewSpec
  "A schema for how things are supposed to be rendered, passed around as declarative query metadata."
  (merge
    {(s/optional-key :attributes) {s/Keyword ComponentAttrs}}
    {(s/optional-key :components) {s/Keyword GenericRenderFn}}
    ComponentAttrs))

;(println DatviewSpec)


;; Would be cool to have a function that computes a schema based on a particular pull expr (and perhaps current db val)
(s/defn PullDataForExpr :- s/Schema
  [db :- DataScriptDB, pull-expr :- PullExpr])

(def GenericPullData
  {s/Keyword s/Any})


;; ## Attribute view

;; View all of the values for some entity, attribute pair
;; Values must be passed in explicitly, or in an atom

(defn lablify-attr-ident
  [attr-ident]
  (let [[x & xs] (clojure.string/split (name attr-ident) #"-")]
    (clojure.string/join " " (concat [(clojure.string/capitalize x)] xs))))

(defn label-view
  [conn pull-expr attr-ident]
  (when attr-ident
    [re-com/label
     :style {:font-size "14px"
             :font-weight "bold"}
     :label
     ;; XXX Again, should be pull-based
     (or @(posh/q conn '[:find ?attr-label .
                         :in $ ?attr-ident
                         :where [?attr :db/ident ?attr-ident]
                                [?attr :attribute/label ?attr-label]]
                  attr-ident)
         (lablify-attr-ident attr-ident))]))



;; ## Attribute metadata reactions

;; We have a single function which gives us the attribute metadata (should rename fn? XXX)

(def attribute-schema-reaction
  "Returns the corresponding attr-ident entry from `datomic-schema-index-reaction`."
  (memoize
    (fn [conn attr-ident]
      (posh/pull conn
                 '[* {:db/valueType [:db/ident]} {:db/cardinality [:db/ident]}]
                 [:db/ident attr-ident]))))

;; Another function gives us a version of this that maps properly to idents
(def attribute-signature-reaction
  "Reaction of the pull of a schema attribute, where any references to something with an ident
  have been replaced by that ident keyword."
  (memoize
    (fn [conn attr-ident]
      (let [schema-rx (attribute-schema-reaction conn attr-ident)]
        (reaction
          (into {}
            (map (fn [[k v]] [k (if-let [ident (:db/ident v)] ident v)])
                 @schema-rx)))))))


;; This code below may get better performance once we get the new posh stuff working...
;; It would be nice though if we had some benchmarking stuff set up to be rigorous.
;; XXX TODO

;(defn ^:deprecated old-attribute-signature
  ;;; Could work if we just used :staying-alive
  ;[conn attr-ident]
  ;(posh/q conn '[:find [?value-type-ident ?iscomp]
                 ;:where [?attr :db/ident ?attr-ident]
                        ;[?attr :db/valueType ?value-type]
                        ;[?value-type :db/ident ?value-type-ident]
                        ;[(get-else $ ?attr :db/isComponent false) ?iscomp]
                 ;:in $ ?attr-ident]
          ;attr-ident))
;; This should be the implementation, but we have to swap out till we get pull in q in posh
;(defn datomic-schema-reaction
  ;"A reaction of the denormalized Datomic schema (anything with :db/ident) as DataScript sees it."
  ;[conn]
  ;;; XXX TODO Mark as :staying-alive true or whatever
  ;(posh/q conn '[:find [(pull [*] ?e) ...] :where [?e :db/ident]]))
;(def datomic-schema-index-reaction
  ;"Returns the datomic-schema-reaction as a map from attr-id to pulls"
  ;(memoize
    ;(fn [conn]
      ;(let [datomic-schema (datomic-schema-reaction conn)]
        ;(reaction
          ;(into {} (map (fn [{:as ident-entity :keys [db/ident]}] [ident ident-entity])
                        ;@datomic-schema)))))))
;(def attribute-schema-reaction
  ;"Returns the corresponding attr-ident entry from `datomic-schema-index-reaction`."
  ;(memoize
    ;(fn [conn attr-ident]
      ;(let [datomic-schema-index (datomic-schema-index-reaction conn)]
        ;(reaction (get @datomic-schema-index attr-ident))))))

;;; XXX TODO For this to work will need to make sure datsync is keeping the conn schema up to date, and has things in the right shape, as well as includes the datomic.db/type
;(def schema-reaction
  ;"A reaction of the schema as DataScript sees it internally."
  ;(memoize
    ;(fn [conn]
      ;(let [conn-rx (as-reaction conn)]
        ;(reaction (:schema @conn-rx))))))

(defn get-nested-pull-expr
  [pull-expr attr-ident]
  (or
    (some (fn [attr-entry]
             (cond
               ;; Not sure if these :component assignments are the right ticket
               (and (keyword? attr-entry) (= attr-entry attr-ident))
               ^{:component summary-view} '[*]
               (and (map? attr-entry) (get attr-entry attr-ident))
               (get attr-entry attr-ident)
               :else false))
          pull-expr)
    ^{:component summary-view} '[*]))

;; Summary needs to be handled somewhat more cleverly... Set up as a special function that returns the corresponding pull-expr component?

(declare pull-data-view)

;(defn) 

(def default-box-style
  {:border "2px solid grey"
   :margin "3px"
   :background-color "#E5FFF6"})

(def default-pull-data-view-style
  (merge h-box-styles
         default-box-style
         {:padding "8px 15px"
          :width "100%"}))


(def default-attr-view-style
  (merge v-box-styles
         {:padding "5px 12px"}))

(def default-mappings
  {:attributes {:attr-values-view {:style h-box-styles}
                :value-view {:style (merge h-box-styles
                                           {:padding "3px"})}
                :attr-view  {:style (merge v-box-styles
                                           {:padding "5px 12px"})}
                :label-view {:style {:font-size "14px"
                                     :font-weight "bold"}}
                :pull-view {:style (merge default-pull-data-view-style)}}})

(def attributes-for-component
  (memoize
    (fn [conn pull-expr component-key]
      ;; derived-attributes? TODO XXX
      (-> default-mappings
          (utils/deep-merge @(default-config conn))
          ;; May need to customize this merge operation XXX
          ;; Uh... also, not sure if the caching will work properly on this if someone changes the pull-expr metadata and it's not in a reaction (or even if it is, cause of equality semantics)
          (utils/deep-merge (meta pull-expr))
          :attributes
          (get component-key)
          ;; And make the class play nicely with specifying other classes XXX
          (->> (merge {:class (name component-key)}))
          reaction))))

(defn value-view
  [conn pull-expr attr-ident value]
  (let [attr-sig @(attribute-signature-reaction conn attr-ident)
        comp-attrs @(attributes-for-component conn pull-expr :value-view)]
    [:div comp-attrs
     ;[debug "Here is the comp-attrs:" attr-sig]
     (match [attr-sig]
       ;; For now, all refs render the same; May treat component vs non-comp separately later
       [{:db/valueType :db.type/ref}]
       [pull-data-view conn (get-nested-pull-expr pull-expr attr-ident) value]
       ;; Miscellaneous value
       :else
       (str value))]))

(defn collapse-summary
  [conn attr-ident values]
  (case attr-ident
    ;; XXX Needs to be possible to hook into the dispatch here
    ;; Default
    [:p "Click the arrow to see more"]))

  
;; Should we have a macro for building these components and dealing with all the state in the config? Did the merge for you?
;(defn build-view-component)

(defn attr-values-view
  [conn pull-expr attr-ident values]
  [:div @(attributes-for-component conn pull-expr :attr-values-view)
   (for [value (utils/deref-or-value values)]
     ^{:id (hash value)}
     [value-view conn pull-expr attr-ident value])])


(defn cardinality
  [conn attr-ident])

;; Need to have controls etc here
(defn attr-view
  [conn pull-expr attr-ident values]
  [:div @(attributes-for-component conn pull-expr :attr-view)
   [label-view conn pull-expr attr-ident]
   (match [@(attribute-signature-reaction conn attr-ident)]
     [{:db/cardinality :db.cardinality/many}]
     [attr-values-view conn pull-expr attr-ident values]
     :else
     [value-view conn pull-expr attr-ident values])])


;(defn attribute-values-view
  ;[conn attr-ident values]
  ;;; This is hacky, take out for datview and query...
  ;(let [collapsable? false ;; Need to get this to dispatch... XXX Or should maybe just default to true for all non-component refs
        ;collapse-attribute? (r/atom false)
        ;;; Need to make this polymorphic/dispatchable
        ;sort-by-key :e/order
        ;sorted-values (reaction (map :db/id (sort-by sort-by-key @(pull-many-rx conn '[:db/id :e/order] values))))]
    ;(fn [conn attr-ident values]
      ;[re-com/v-box
       ;:padding "8px"
       ;:gap "8px"
       ;:children [[re-com/h-box
                   ;:children [(when collapsable?
                                ;[collapse-button collapse-attribute?])
                              ;[label-view conn attr-ident]]]
                  ;(if (and collapsable? @collapse-attribute?)
                    ;^{:key 1}
                    ;[collapse-summary conn attr-ident values]
                    ;(for [value @values-rx]
                      ;^{:key (hash {:component :attr-view :value value})}
                      ;[value-view conn attr-ident value]))]])))


;; ## Security

;; All messages have signatures; or can
;; Can b e used to assert the h8istry of things

;^{:set asside}

;; You can even save your datview metadata-query structures in the database :-)
;; You can build these things atomically
;; It's the perfect backbone, really
;; Subsets fine


;; All rendering modes should be controllable via registered toggles or fn assignments
;; registration modules for plugins
;; * middleware?


;(def DataScriptDB
  ;(s/protocol ID))

;;; directly use d/db?
;(def Conn
  ;(s/protocol)) 

;; Should actually try to tackle this

;(s/defn pull-data-view :- Hiccup
  ;"Given a DS connection, a datview pull-expression and data from that pull expression (possibly as a reaction),
  ;render the UI subject to the pull-expr metadata."
  ;;; Should be able to bind the data to the type dictated by pull expr
  ;([conn :- Conn, pull-expr :- PullExpr, pull-data :- GenericPullData]
(defn pull-data-view
  "Given a DS connection, a datview pull-expression and data from that pull expression (possibly as a reaction),
  render the UI subject to the pull-expr metadata."
  ;; Should be able to bind the data to the type dictated by pull expr
  ([conn, pull-expr, pull-data]
   ;; Annoying to have to do this
   (let [default-config-reaction (default-config conn)
         pull-meta (meta pull-expr)]
     (fn [db pull-expr pull-data]
       (let [default-config @default-config-reaction
             config (utils/deep-merge default-config pull-meta)
             pull-data (utils/deref-or-value pull-data)]
         [:div (utils/deep-merge {:style default-pull-data-view-style
                                  :class "pull-data-view"}
                                 (:pull-view config))
          (when-let [controls (:controls config)]
            [controls conn pull-expr pull-data])
          (when-let [summary (:summary config)]
            [:div {:style (merge h-box-styles)}
             [summary conn pull-expr pull-data]])
          ;; XXX TODO Questions:
          ;; Need a react-id function that lets us repeat attrs when needed
          ;; Can we just use indices here?
          ;; How do we handle *?
          (for [pull-attr (distinct pull-expr)]
            (let [attr-ident (cond (keyword? pull-attr) pull-attr
                                   (map? pull-attr) (first (keys pull-attr)))]
              ^{:key (hash pull-attr)}
              [attr-view conn pull-expr attr-ident (get pull-data attr-ident)]))])))))

        ;[re-com/v-box
         ;;:padding "10px"
         ;:style style
         ;:gap "10px"
         ;:children [;; Little title bar thing with controls
                    ;(when controls
                      ;[re-com/h-box
                       ;:justify :end
                       ;:padding "15px"
                       ;:gap "10px"
                       ;;:style {:background "#DADADA"}
                       ;:children [controls conn]])]]

                    ;[re-com/h-box
                     ;;:align :center
                     ;:gap "10px"
                     ;:children [[re-com/v-box
                                 ;:padding "15px"
                                 ;:children [[entity-summary conn eid]]]
                                ;[re-com/v-box
                                 ;:children (for [[attr-ident values] pull-data]
                                             ;;; Dynamatch the id functions?
                                             ;^{:key (hash attr-ident values)}
                                             ;[attribute-values-view conn attr-ident values])]]]

(defn pull-view
  ([conn pull-expr eid]
   [pull-data-view conn pull-expr (posh/pull conn pull-expr eid)]))


;; General purpose sortable collections in datomic/ds?
;; Should use :attribute/sort-by; default :db/id?


(defn attr-sort-by
  [conn attr-ident]
  (reaction (or (:db/ident (:attribute/sort-by @(posh/pull conn '[*] [:db/ident attr-ident])))
                ;; Should add smarter option for :e/order as a generic? Or is this just bad semantics?
                :db/id)))

(defn value-type
  [conn attr-ident]
  (reaction (:db/valueType @(posh/pull conn '[*] [:db/ident attr-ident]))))

(defn reference?
  [conn attr-ident values]
  (reaction (= (value-type conn attr-ident) :db.type/ref)))

;; Can add matches to this to get different attr-idents to match differently; Sould do multimethod?
;; Cardinality many ref attributes should have an :attribute.ref/order-by attribute, and maybe a desc option
;; as well
(defn sorted-values
  [conn attr-ident values]
  (reaction (if @(reference? conn attr-ident values)
              (sort-by @(attr-sort-by conn attr-ident) values)
              (sort values))))

