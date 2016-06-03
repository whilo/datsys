(ns datview.core
  "# Datview"
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [posh.core :as posh]
            [schema.core :as s
             :include-macros true]
            [com.stuartsierra.component :as component]
            [datview.schema :as datview.s]
            [datview.router :as router]
            [datview.comms :as comms]
            [datview.utils :as utils]
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


;; Should really remove `conn` everywhere below to `component`, for access to other resources

(defn send-message!
  [{:as datview :keys [comms]} message]
  (comms/send-message! comms message))

(defn send-tx!
  "Sends the transaction to Datomic via datsync/datomic-tx and whatever send-message! function is defined
  (message = [:datsync.remote/tx tx])"
  [{:as datview :keys [comms conn]} tx]
  (js/console.log "Sending tx:" (pr-str tx))
  (let [datomic-tx (datsync/datomic-tx conn tx)]
    (js/console.log "Remote tx translated:" (pr-str datomic-tx))
    (comms/send-message! comms [:datsync.remote/tx datomic-tx])
    ;; This was from hard coded sente
    #_(ws/chsk-send! [:datsync.remote/tx datomic-tx])))



;; ## Metadata view specification structure defaults

(def ^:dynamic box-styles
  {:display "inline-flex"
   :flex-wrap "wrap"})

(def ^:dynamic h-box-styles
  (merge box-styles
         {:flex-direction "row"}))

(def ^:dynamic v-box-styles
  (merge box-styles
         {:flex-direction "column"}))

(def bordered-box-style
  {:border "2px solid grey"
   :margin "3px"
   :background-color "#E5FFF6"})

(def default-pull-data-view-style
  (merge h-box-styles
         {:padding "8px 15px"
          :width "100%"}))

(def default-attr-view-style
  (merge v-box-styles
         {:padding "5px 12px"}))


(def default-mappings (r/atom {}))

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



;; ## Context

;; We're going to be re-describing things in terms of context.
;; Context includes configuration and contextual information about where things are.
;; But it is extensible, so we can pass through whatever information we might like about how to render things.

;; All of these should be checked for their semantics on :datview.base-context/value etc; Is this the right way to represent these things?

;; Should probably move all of these out to reactions or some such, except for anything that's considered public

(def base-context
  ;; Not sure if this memoize will do what I'm hoping it does (:staying-alive true, effectively)
  (memoize
    (fn [datview]
      ;; Hmm... should we just serialize the structure fully?
      ;; Adds complexity around wanting to have namespaced attribute names for everything
      (reaction (:datview.base-context/value @(posh/pull (:conn datview) '[*] [:db/ident :datview/base-context]))))))

(defn update-base-context!
  [datview f & args]
  (letfn [(txf [db]
            (apply update
                   (d/pull db '[*] [:db/ident :datview/base-context])
                   :datview.base-context/value
                   f
                   args))]
    (d/transact! (:conn datview) [[:db.fn/call txf]])))

(defn set-base-context!
  [datview context]
  (update-base-context! (:conn datview) (constantly context)))



;; ## Reactions

(defn as-reaction
  "Treat a regular atom as though it were a reaction"
  [vanilla-atom]
  (let [trigger (r/atom 0)]
    (add-watch vanilla-atom :as-reaction-trigger (fn [& args] (swap! trigger inc)))
    (reaction
      @trigger
      @vanilla-atom)))

(defn pull-many
  [datview pattern eids]
  (let [conn-reaction (as-reaction (:conn datview))]
    (reaction (d/pull-many @conn-reaction pattern eids))))


(defn meta-sig
  [args-vec]
  (mapv #(vector % (meta %)) args-vec))

(defn meta-memoize
  ([f]
   ;; Don't know if this actually has to be an r/atom; may be more performant for it not to be
   (meta-memoize f (r/atom {})))
  ([f cache]
   (fn [& args]
     (if-let [cached-val (get @cache (meta-sig args))] 
       cached-val
       (let [new-val (apply f args)]
         (swap! cache assoc (meta-sig args) new-val)
         new-val)))))

;; ### Attribute metadata reactions

(def attribute-schema-reaction
  "Returns the corresponding attr-ident entry from the Datomic schema. Returns full entity references; Have to path for idents."
  (memoize
    (fn [datview attr-ident]
      (if (= attr-ident :db/id)
        (reaction {:db/id nil})
        (posh/pull (:conn datview)
                   '[* {:db/valueType [:db/ident]
                        :db/cardinality [:db/ident]
                        :attribute.ref/types [:db/ident]}]
                   [:db/ident attr-ident])))))

;; Another function gives us a version of this that maps properly to idents
(def attribute-signature-reaction
  "Reaction of the pull of a schema attribute, where any references to something with any ident entity
  have been replaced by that ident keyword."
  (memoize
    (fn [datview attr-ident]
      (let [schema-rx (attribute-schema-reaction (:conn datview) attr-ident)]
        (reaction
          (into {}
            (letfn [(mapper [x]
                      (or (:db/ident x)
                          (and (sequential? x) (map mapper x))
                          x))]
              (map (fn [[k v]] [k (mapper v)])
                   @schema-rx))))))))


;; This is what does all the work of computing our context for each component

(def component-context
  "This function returns the component configuration (base-context; should rename) for either an entire render network,
  abstractly, or for a specific component based on a component id (namespaced keyword matching the function to be called)."
  (memoize
    (fn component-context*
      ([datview]
       (reaction
         ;; Don't need this arity if we drop the distinction between base-context and default-mappings
         (utils/deep-merge
           @default-mappings
           @(base-context datview))))
      ([datview component-id]
       (component-context* (:conn datview) component-id {}))
      ([datview component-id {:as options}
                           ;; Options, in order of precedence in consequent merging
                           :keys [datview/locals ;; points to local overrides; highest precedence
                                  ;; When the component is in a scope closed over by some particular attribute:
                                  datview/attr ;; db/ident of the attribute; precedence below locals
                                  datview/valueType ;; The :db/valueType of the attribute (as ident); lower precedence still
                                  datview/cardinality]] ;; Cardinality (ident) of the value type; lowest precedence
       (reaction
         (let [conn (:conn datview)
               merged (utils/deep-merge @(component-context conn) (utils/deref-or-value locals))]
           (if attr
             (let [attr-sig @(attribute-signature-reaction conn attr)]
               (utils/deep-merge (get-in merged [:datview/base-config component-id])
                                 (get-in merged [:datview/card-config (:db/cardinality attr-sig) component-id])
                                 (get-in merged [:datview/value-type-config (:db/valueType attr-sig) component-id])
                                 (get-in merged [:datview/attr-config attr component-id])))
             ;; Need to also get the value type and card config by the attr-config if that's all that's present; Shouldn't ever
             ;; really need to pass in manually XXX
             (get-in merged [:datview/base-config component-id]))))))))



;; ## DataScript schema

;; Some basic schema that needs to be transacted into the database in order for these functions to work

(def base-schema
  {:datview.base-context/value {}})

(def default-settings
  [{:db/ident :datview/base-context
    :datview.base-context/value {}}])

;; Have to think about how styles should be separated from container structure, etc, and how things like
;; little control bars can be modularly extended, etc.
;; How can this be modularized enough to be truly generally useful?

;; These should be moved into styles ns or something



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

(defn pull-summary
  [pull-data]
  (match [pull-data]
    [{:e/name name}] name
    [{:e/type {:db/ident type-ident}}] (name type-ident)
    [{:attribute/label label}] label
    ;; A terrible assumption really, but fine enough for now
    :else (pr-str pull-data)))

(defn pull-summary-view
  [datview context pull-data]
  [:div {:style {:font-weight "bold" :padding "5px"}}
   (pull-summary pull-data)])

(defn collapse-summary
  [datview context values]
  ;; XXX Need to stylyze and take out of re-com styling
  [:div {:style (merge v-box-styles
                       {:padding "10px"})}
                       ;:align :end
                       ;:gap "20px"
   (for [value values]
     ^{:key (hash value)}
     [pull-summary-view datview context value])])

;; These summary things are still kinda butt ugly.
;; And they're something we need to generally spend more time on anyway.
;; Need to smooth out... XXX



;; ## Event handler

;; Need an even handler which can dispatch on some transaction patterns, and execute various messages or side effects.
;; I think posh may give this to us?
;; Or did in an old version?


;; ## Datview schema spec


;; ## Import

;; This is a great ingestion format
;; Make it possible to build semantic parsers for data on top of other web pages :-)



;; ## Attribute view

;; View all of the values for some entity, attribute pair
;; Values must be passed in explicitly, or in an atom

(defn lablify-attr-ident
  [attr-ident]
  (let [[x & xs] (clojure.string/split (name attr-ident) #"-")]
    (clojure.string/join " " (concat [(clojure.string/capitalize x)] xs))))

(defn label-view
  [datview attr-ident]
  (when attr-ident
    [re-com/label
     :style {:font-size "14px"
             :font-weight "bold"}
     :label
     ;; XXX Again, should be pull-based
     (or @(posh/q (:conn datview) '[:find ?attr-label .]
                         :in $ ?attr-ident
                         :where [?attr :db/ident ?attr-ident]
                                [?attr :attribute/label ?attr-label]
                  attr-ident)
         (lablify-attr-ident attr-ident))]))




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

;; XXX This will be coming to posh soon, but in case we need it earlier

;; Still have to implement notion of hidden attributes at a database level


;; Still need to hook up with customized context
(defn pull-view-controls
  [datview pull-expr pull-data]
  (let [pull-data (utils/deref-or-value pull-data)
        view-spec (meta pull-expr)]
    [:div (:dom/attrs @(component-context datview ::pull-view-controls {:datview/locals (meta pull-expr)}))
     [re-com/md-icon-button :md-icon-name "zmdi-copy"
                            :size :smaller
                            :style {:margin-right "10px"}
                            :tooltip "Copy entity"
                            :on-click (fn [] (js/alert "Coming soon to a database application near you"))]
     [re-com/md-icon-button :md-icon-name "zmdi-edit"
                            :style {:margin-right "10px"}
                            :size :smaller
                            :tooltip "Edit entity"
                            ;; This assumes the pull has :datsync.remote.db/id... automate?
                            :on-click (fn [] (router/set-route! datview {:handler :edit-entity :route-params {:db/id (:datsync.remote.db/id pull-data)}}))]]))

(defn default-field-for-controls
  [datview pull-expr pull-data]
  (let [context (component-context datview ::default-field-for-controls {:datview/locals (meta pull-expr)}]
    [:div (:dom/attrs context)])) 

;(defn)

(defn value-view
  [datview pull-expr attr-ident value]
  (let [conn (:conn datview)
        attr-sig @(attribute-signature-reaction conn attr-ident)
        context @(component-context conn ::value-view {:datview/locals (meta pull-expr)})]
    [:div (:dom/attrs context)
     ;[debug "Here is the comp-attrs:" attr-sig]
     (match [attr-sig]
       ;; For now, all refs render the same; May treat component vs non-comp separately later
       [{:db/valueType :db.type/ref}]
       [pull-data-view datview (get-nested-pull-expr pull-expr attr-ident) value]
       ;; Miscellaneous value
       :else
       (str value))]))

  

;; Should we have a macro for building these components and dealing with all the state in the context? Did the merge for you?
;(defn build-view-component)

(defn attr-values-view
  [datview pull-expr attr-ident values]
  (let [context @(component-context (:conn datview) ::attr-values-view {:datview/locals (meta pull-expr)})
        collapsable? (:datview.collapse/collapsable? context)
        ;; Should put all of the collapsed values in something we can serialize, so we always know what's collapsed
        collapse-attribute? (r/atom (:datview.collapse/default context))]
    (fn [datview pull-expr attr-ident values]
      [:div (:dom/attrs context)
       (when collapsable?
         [collapse-button collapse-attribute?])
       (when @collapse-attribute?
         [collapse-summary datview context values])
          ;(defn pull-summary-view [datview pull-expr pull-data]
       (when (or (not collapsable?) (and collapsable? (not @collapse-attribute?)))
         (for [value (utils/deref-or-value values)]
           ^{:key (hash value)}
           [value-view datview pull-expr attr-ident value]))])))



;; Need to have controls etc here
(defn attr-view
  [datview pull-expr attr-ident values]
  [:div (:dom/attrs @(component-context datview ::attr-view {:datview/locals (meta pull-expr)})) 
  ;[:div @(attribute-context datview (meta pull-expr) :attr-view)
   [label-view datview attr-ident]
   (match [@(attribute-signature-reaction datview attr-ident)]
     [{:db/cardinality :db.cardinality/many}]
     [attr-values-view datview pull-expr attr-ident values]
     :else
     [value-view datview pull-expr attr-ident values])])


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


(defn pull-attributes
  ([pull-expr pull-data]
   (->> pull-expr
        (map (fn [attr-spec]
               (cond
                 (keyword? attr-spec) attr-spec
                 (map? attr-spec) (keys attr-spec)
                 (symbol? attr-spec)
                 (case attr-spec
                   '* (filter
                        (set (pull-attributes (remove #{'*} pull-expr) []))
                        (keys pull-data))))))
        flatten
        distinct))
  ([pull-expr]
   (pull-attributes pull-expr [])))

;; Should actually try to tackle this

(defn pull-data-view
  "Given a DS connection, a datview pull-expression and data from that pull expression (possibly as a reaction),
  render the UI subject to the pull-expr metadata."
  ;; Should be able to bind the data to the type dictated by pull expr
  ([datview, pull-expr, pull-data]
   ;; Annoying to have to do this
   (let [context @(component-context datview ::pull-data-view {:datview/locals (meta pull-expr)})
         pull-data (utils/deref-or-value pull-data)]
     [:div (:dom/attrs context)
      [:div
        (when-let [controls @(component-context datview ::pull-view-controls)]
          [(:datview/component controls) datview pull-expr pull-data])
        (when-let [summary (:summary context)]
          [:div {:style (merge h-box-styles)}
           [summary datview pull-expr pull-data]])]
      ;; XXX TODO Questions:
      ;; Need a react-id function that lets us repeat attrs when needed
      (for [attr-ident (pull-attributes pull-expr pull-data)]
        ^{:key (hash attr-ident)}
        [attr-view datview pull-expr attr-ident (get pull-data attr-ident)])])))

(defn pull-view
  ([datview pull-expr eid]
   [pull-data-view datview pull-expr (posh/pull (:conn datview) pull-expr eid)]))


;; General purpose sortable collections in datomic/ds?
;; Should use :attribute/sort-by; default :db/id?


(defn attr-sort-by
  [datview attr-ident]
  (reaction (or (:db/ident (:attribute/sort-by @(posh/pull (:conn datview) '[*] [:db/ident attr-ident])))
                ;; Should add smarter option for :e/order as a generic? Or is this just bad semantics?
                :db/id)))

(defn value-type
  [datview attr-ident]
  (reaction (:db/valueType @(posh/pull (:conn datview) '[*] [:db/ident attr-ident]))))

(defn reference?
  [datview attr-ident values]
  (reaction (= (value-type datview attr-ident) :db.type/ref)))

;; Can add matches to this to get different attr-idents to match differently; Sould do multimethod?
;; Cardinality many ref attributes should have an :attribute.ref/order-by attribute, and maybe a desc option
;; as well
(defn sorted-values
  [datview attr-ident values]
  (reaction (if @(reference? datview attr-ident values)
              (sort-by @(attr-sort-by datview attr-ident) values)
              (sort values))))


;; Setting default context; Comes in precedence even before the DS context
;; But should this be config technically?
;; Note: There are function values in here, so some of this would not be writable to Datomic; But at least some of it could be...)
;(reset! default-mappings
(swap! default-mappings
  utils/deep-merge
  ;; Top level just says that this is our configuration? Or is that not necessary?
  {:datview/base-config
   {::attr-values-view
    {:dom/attrs {:style h-box-styles}
     :datview/component attr-values-view
     ;; Right now only cardinality many attributes are collapsable; Should be able to set any? Then set for cardinality many as a default? XXX
     :datview.collapse/collapsable? true
     :datview.collapse/default false} ;; Default; nothing is collapsed
    ::value-view
    {:dom/attrs {:style (merge h-box-styles
                               {:padding "3px"})}
     :datview/component value-view}
    ::attr-view
    {:dom/attrs {:style (merge v-box-styles
                               {:padding "5px 12px"})}
     :datview/component attr-view}
    ::label-view
    {:dom/attrs {:style {:font-size "14px"
                         :font-weight "bold"}}
     :datview/component label-view}
    ::pull-data-view
    {:dom/attrs {:style (merge h-box-styles
                               bordered-box-style
                               {:padding "8px 15px"
                                :width "100%"})}
     ;; Hmm... maybe this should point to the keyword so it can grab from there?
     :datview/summary pull-summary-view
     :datview/component pull-view}
    ::pull-view-controls
    {:dom/attrs {:style (merge h-box-styles
                               {:padding "5px"})}
                                ;:background "#DADADA"})}
                                ;;; Check if these actually make sense
                                ;:justify-content "flex-end"})}}
                                ;:gap "10px"
     :datview/component pull-view-controls}
    ::pull-summary-view
    {:dom/attrs {:style (merge v-box-styles
                               {:padding "15px"
                                :font-size "18px"
                                :font-weight "bold"})}
     :datview/component pull-summary-view}
    ::default-field-for-controls
    {:datview/component default-field-for-controls}
    :datview.forms/field-for
    {:dom/attrs {:style v-box-styles}}}
   ;; Specifications merged in for any config
   :datview/card-config {}
   ;; Specifications merged in for any value type
   :datview/value-type-config {}
   :datview/attr-config {:db/id {:datview.forms/field-for {:attribute/hidden? true
                                                           :dom/attrs {:style {:display "none"}}}}}})
   ;; Will add the ability to add mappings at the entity level; And perhaps specifically at the type level.
   ;; Use the patterns of OO/types with pure data; Dynamic


;; Here's where everything comes together
;; Datview record instances are what we pass along to our Datview component functions as the first argument.
;; Abstractly, they are just a container for your database and communications functionality (via attributes :conn and :config).
;; But in reality, they are actually Stuart Sierra components, with start and stop methods.
;; You can either use these components standalone, by creating your datview instance with `(new-datview ...)`, and starting it with the `start` function (both defined below).
;; Just know that 

(defrecord Datview 
  ;;  The public API: these two attributes
  [conn ;; You can access this for your posh queries;
   config ;; How you control the instantiation of Datview; options:
   ;; * :datascript/schema
   ;; * 
   comms ;; Actual component dependency; Something implementing the datview.comms protocols
   message-handler ;; message-handler function, if obtainable, of comms
   message-chan] ;; message-chan, if obtainable via comms
  component/Lifecycle
  (start [component]
    (js/console.log "Starting Datview")
    (let [base-schema (utils/merge datsync/base-schema (:datascript/schema config))
          conn (or conn (d/create-conn datsync/base-schema))
          message-chan (if (satisfies?))]
      (d/transact! conn datview/default-settings)
      (posh/posh! conn) ;; Not sure if this is the best place for this
      (assoc component
             :conn conn
             :comms)))
  (stop [component]
    component))


(defn new-datview
  ([options]
   (map->Datview {:options options}))
  ([]
   (new-datview {})))


