(ns catalysis.client.datview
  "# Datview"
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [posh.core :as posh]
            [reagent.core :as r]
            [datascript.core :as d]))


;; ## Overview

;; What if you could write views which compute themselves based on the data you pass in?
;; Datomic's rich and flexilible data-driven schema make it possible to do this very naturally.

;; However, there are challenges to this... XXX finish


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

(defn vbox
  [& {:as kvs}]
  (->> kvs
       (u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       seq
       flatten
       (apply re-com/v-box)))

(defn hbox
  [& {:as kvs}]
  (->> kvs
       (u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       (u/deep-merge {:coll-merge (comp vec concat)} my-vbox-defaults)
       seq
       flatten
       (apply re-com/h-box)))


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

;; These definitely need some cleaup... Not sure which are being used and which aren't.
;; And defintely would like to not have to refer to entity ids all the time.

(def rules
  ;; Cardinality getters
  '[[(attr-card ?attr ?card)
     [?attr :db/cardinality ?card]]
    [(attr-ident-card ?attr-ident ?card)
     [?attr :db/ident ?attr-ident]
     (attr-card ?attr ?card)]
    [(attr-ident-card-ident ?attr-ident ?card-ident)
     [?card :db/ident ?card-ident]
     (attr-ident-card ?attr-ident ?card)]
    [(attr-card-ident ?attr ?card-ident)
     [?card :db/ident ?card-ident]
     (attr-card ?attr-ident ?card)]
    ;; Component getters
    [(attr-iscomp? ?attr ?comp?)
     [(get-else $ ?attr :db/isComponent false) ?comp?]]
    [(attr-ident-iscomp? ?attr-ident ?comp?)
     [?attr :db/ident ?attr-ident]
     (attr-iscomp? ?attr ?comp?)]
    ;; Uhh... and more cause the above doesn't always work...
    [(attr-iscomp ?attr)
     [?attr :db/isComponent true]]
    [(attr-ident-iscomp ?attr-ident)
     [?attr :db/ident ?attr-ident]
     [attr-iscomp ?attr]]
    [(attr-isnotcomp ?attr)
     [(get-else $ ?attr :db/isComponent false) ?iscomp?]
     [(not= ?iscomp true)]]
    [(attr-ident-isnotcomp ?attr-ident)
     [?attr :db/ident ?attr-ident]
     [attr-isnotcomp ?attr]]
    ;; Value type
    [(attr-value-type ?attr ?value-type)
     [?attr :db/valueType ?value-type]]
    [(attr-ident-value-type ?attr-ident ?value-type)
     [?attr :db/ident ?attr-ident]
     [?attr :db/valueType ?value-type]]
    [(attr-value-type-ident ?attr ?value-type-ident)
     [?value-type :db/ident ?value-type-ident]
     [?attr :db/valueType ?attr-ident]]
    [(attr-ident-value-type-ident ?attr-ident ?value-type-ident)
     (attr-ident-value-type ?attr-ident ?value-type)
     [?value-type :db/ident ?value-type-ident]]

    [(eid-attr-idents ?eid ?attr-ident)
     [?eid ?attr-ident]]

    [(eid-attr-idents ?eid ?attr-ident)
     [?eid :e/type ?type-ident]
     (type-ident-attr-ident ?type-ident ?attr-ident)]

    [(type-ident-attr-ident ?type-ident ?attr-ident)
     [?type :db/ident ?type-ident]
     (type-attr-ident ?type ?attr-ident)]
    [(type-attr-ident ?type ?attr-ident)
     [?attr :db/ident ?attr-ident]
     (type-attr ?type ?attr)]
    [(type-attr ?type ?attr)
     [?type :e.type/attributes ?attr]]
    [(type-attr ?type ?attr)
     [?type :e.type/isa ?type-ancestor]
     [?type-ancestor :e.type/attributes ?attr]]

    ;; Should build a rule-doc system for datalog rules!
    ^{:doc "Join attr-ident to type-idents consistent with :attribute.ref/types, given any :e/isa relationships."}
    [(attr-ident-type-ident ?attr-ident ?type-ident)
     [?attr :db/ident ?attr-ident]
     (attr-type-ident ?attr ?type-ident)]

    ^{:doc "Join attr ids to type idents consistent with :attribute.ref/types, given any :e/isa relationships."}
    ;; First, our direct assignment
    [(attr-type-ident ?attr ?type-ident)
     [?type :db/ident ?type-ident]
     (attr-type ?attr ?type)]

    ^{:doc "Join attr id to type id consistent with :attribute.ref/types, given any :e/isa relationships."}
    ;; First, our direct assignment
    [(attr-type ?attr ?type)
     [?attr :attribute.ref/types ?type]]
    ;; Next, our recursion
    ^{:doc "Recursive rule for attr-type-ident"}
    [(attr-type ?attr ?type)
     [?type :e.type/isa ?abstract-type]
     (attr-type ?attr ?abstract-type)]

    ^{:doc "Abstract isa relationship"}
    [(isa ?type1 ?type2)
     [?type1 :e.type/isa ?type2]]

    ;; Is it possible to come up with

          ;:where [?attr :db/ident ?attr-ident]
                 ;[?attr :attribute.ref/types ?type]
                 ;[?type :db/ident ?type-ident]]

    ;; More?
    ])


;; ## Entity views

;; Everything is based around the entity-view component.
;; Given a connection and an eid, it produces a pull view for that eid.

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

(defn entity-summary-string
  [conn eid]
  (let [entity @(posh/pull conn '[*] eid)]
    (entity-name entity)))


;; Still not sure where we should be assuming eid vs entity; should these all be flexible somehow?
(defn entity-summary
  [conn eid]
  (let [entity @(posh/pull conn '[*] eid)]
    (match [entity]
      ;; XXX Need to build dynamatch hooks in here
      :else
      [re-com/title
       :level :level3
       :style {:font-weight "bold"
               :font-size "18px"}
       :label (entity-name entity)])))

;; ### Attribute signatures

;; We often seem to care about what kind of attribute we're looking at.
;; Mostly, we tend to care about categorization at the level of value, reference or reference component.
;; Things feel a little hodge-podge here though at the moment...
;; Should try to think about how all this signature stuff can be made more general/composable.

;; XXX Need to rewrite all of this in terms of pulls; should also make it possible to specify eid instead or [:db/ident attr-ident]
;; Also, need to figure out if there is a way of saving more of our work here so that the reactions aren't
;; getting recomputed over and over
;; Pull would be a lot easier if we got idents back...
;; So pull should look something like
;; [* {:db/cardinality [:db/ident]} {:db/valueType [*]}]

(defn attribute-signature
  [conn attr-ident]
  (posh/q conn '[:find [?value-type-ident ?iscomp]
                 :where [?attr :db/ident ?attr-ident]
                        [?attr :db/valueType ?value-type]
                        [?value-type :db/ident ?value-type-ident]
                        [(get-else $ ?attr :db/isComponent false) ?iscomp]
                 :in $ ?attr-ident]
          attr-ident))

;; Need to distinguish between when we're getting attributes for an entity (just based on what attributes it
;; actually has), versus pulling from the entity's type (or just a type-ident), or some combination, and
;; formalize our notions around this.
;; Included should be our knowledge of orderable attributes.
;; Maybe we can compose rules in some sort of sorter function?
;; This could be a very generally useful thing...

;; XXX Similarly, here we should make things more uniform and try to fix the performance issues by being
;; smarter about these query lifecycles...

(defn signed-attributes
  [conn eid]
  (posh/q conn '[:find ?value-type-ident ?iscomp ?attr-ident
                 :where (eid-attr-idents ?eid ?attr-ident)
                        [?attr :db/ident ?attr-ident]
                        [?attr :db/valueType ?value-type]
                        [?value-type :db/ident ?value-type-ident]
                        [(get-else $ ?attr :db/isComponent false) ?iscomp]
                 :in $ % ?eid]
          rules
          eid))


;; XXX Again, rewrite to be smart about performance and overquerying
(defn value-view
  ([conn attr-ident value]
   (let [signature @(attribute-signature conn attr-ident)]
     [re-com/v-box
      :padding "3px"
      :children
      [(match [signature]
         ;; We have an isComponent ref
         [[:db.type/ref true]]
         [entity-view conn value]
         ;; A non component ref; just return a summary
         [[:db.type/ref false]]
         (entity-summary-string conn value)
         ;; Miscellaneous value
         :else
         (str value))]])))

(defn lablify-attr-ident
  [attr-ident]
  (let [[x & xs] (clojure.string/split (name attr-ident) #"-")]
    (clojure.string/join " " (concat [(clojure.string/capitalize x)] xs))))

(defn label-view [conn attr-ident]
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
       (lablify-attr-ident attr-ident))])


(defn collapse-summary
  [conn attr-ident values]
  (case attr-ident
    ;; XXX Needs to be possible to hook into the dispatch here
    ;; Default
    [:p "Click the arrow to see more"]))
  

;; XXX Should try getting this or something similar in posh
(defn pull-many-rx
  [conn pattern eids]
  (let [conn-reaction (as-reaction conn)]
    (reaction (d/pull-many @conn-reaction pattern eids))))


;; For now...
(def sortable (constantly false))

;; 
(defn attribute-view
  [conn attr-ident values]
  ;; This is hacky, take out for datview and query...
  (let [collapsable? true ;; Need to get this to dispatch... XXX
        collapse-attribute? (r/atom true)
        values-rx (if (sortable? conn attr-ident)
                    (reaction (map :db/id (sort-by :e/order @(pull-many-rx conn '[:db/id :e/order] values))))
                    (reaction (sort values)))]
    (fn [conn attr-ident values]
      [re-com/v-box
       :padding "8px"
       :gap "8px"
       :children
       [[re-com/h-box
         :children
         [(when collapsable?
            [collapse-button collapse-attribute?])
          [label-view conn attr-ident]]]
        (if (and collapsable? @collapse-attribute?)
          ^{:key 1}
          [collapse-summary conn attr-ident values]
          (for [value @values]
            ^{:key (hash {:component :attribute-view :value value})}
            [value-view conn attr-ident value]))]])))


;; The following several functions are for splitting up attributes; this approach feels rather flawed.
;; Firstly, using the signed-attributes function/reaction seems a bit more appropriate here.
;; This is currently used in the forms.
;; It or something like it should be a more standard basis for these kind of decisions and branching.
;; However, it's not clear what the performance implications are here.
;; Right now (below) we're querying for attributes and attribute values at the same time.
;; Not sure if this is better or worse than the alternative.
;; But in any case, things should be cleaned up and standardized a bit.

;; XXX This should be based on attribute metadata most likely, with overrides possible...
(def hidden-attributes
  #{:db/id :e/type :e/order})

(defn visible-attribute?
  [attr-ident]
  (not (hidden-attributes attr-ident)))

(defn ref-attrs
  [conn eid]
  (posh/q conn '[:find ?attr-ident ?value ?iscomp
                 :in $ % ?eid ?visible-attribute?
                 :where [?eid ?attr-ident ?value]
                        (attr-ident-value-type-ident ?attr-ident :db.type/ref)
                        [(?visible-attribute? ?attr-ident)]
                        [?attr :db/ident ?attr-ident]
                        [(get-else $ ?attr :db/isComponent false) ?iscomp]]
          rules
          eid
          visible-attribute?))

(defn non-ref-attrs
  [conn eid]
  (posh/q conn '[:find ?attr-ident ?value
                 :in $ % ?eid ?visible-attribute?
                 :where [?eid ?attr-ident ?value]
                        (attr-ident-value-type-ident ?attr-ident ?vtype-ident)
                        [(not= ?vtype-ident :db.type/ref)]
                        [(?visible-attribute? ?attr-ident)]]
          rules
          eid
          visible-attribute?))

(defn non-component-attributes
  [conn eid]
  (let [non-refs @(non-ref-attrs conn eid)
        refs  @(ref-attrs conn eid)]
    ;; This part is basically a hack; should be able to separately query for comp or noncomp but can't cause
    ;; of no not and unification bug with get-else XXX
    (concat non-refs
            (->> refs
                 (remove last)
                 (map butlast)))))

(defn component-attributes
  [conn eid]
  (let [refs  @(ref-attrs conn eid)]
    (->> refs
         (filter last)
         (map butlast))))

(defn ordered-attributes
  [conn eid]
  (concat (non-component-attributes conn eid)
          (component-attributes conn eid)))


;; ### entity-view

;; All of that for this :-) Our entity-view component.

(defn attribute-values-view
  [conn eid attr-val-pairs]
  [re-com/h-box
   :style {:flex-grow 1}
   :children
   (vec
     (for [[attr-ident values] (group-by first attr-val-pairs)]
       (let [values (map second values)]
         ^{:key (hash {:component :entity-view :eid eid :attr-ident attr-ident :values values})}
         [attribute-view conn attr-ident values])))])

(defn entity-view
  ([conn eid] (entity-view conn eid nil))
  ([conn eid controls]
   (let [entity @(posh/pull conn '[*] eid)
         non-component-attributes (non-component-attributes conn eid)
         component-attributes (component-attributes conn eid)]
     [re-com/border
      :border "2px solid grey"
      :margin "7px"
      :style {:background-color "#E5FFF6"}
      :child
      [re-com/v-box
       ;:padding "10px"
       :gap "10px"
       :children
       [;; Little title bar thing with controls
        (when controls
          [re-com/h-box
           :justify :end
           :padding "15px"
           :gap "10px"
           :style {:background "#DADADA"}
           :children controls])
        [re-com/h-box
         ;:align :center
         :gap "10px"
         :children
         [[re-com/v-box
           :padding "15px"
           :children [[entity-summary conn eid]]]
          [re-com/v-box
           :children 
           [[attribute-values-view conn eid non-component-attributes]
            [attribute-values-view conn eid component-attributes]]]]]]]])))
       


(defn entity-view-with-controls
  [conn eid]
  (let [entity @(posh/pull conn '[*] eid)]
    (entity-view conn eid [[re-com/md-icon-button :md-icon-name "zmdi-copy"
                                                  :tooltip "Copy entity"
                                                  :on-click (fn [] (js/alert "Coming soon to a database application near you"))]
                           [re-com/md-icon-button :md-icon-name "zmdi-edit"
                                                  :tooltip "Edit entity"
                                                  :on-click (fn [] (router/set-route! conn {:handler :edit-entity :route-params {:db/id (:datsync.remote.db/id entity)}}))]])))



;; ## Entity forms
;; ===============

;; Our next goal is to create mostly automated rendering and state management of entity forms

(declare edit-entity-fieldset)


;; XXX These should be moved out into some other namespace; maybe datsync
(defn send-tx!
  "Sends the transaction to Datomic via datsync/datomic-tx and ws/chsk-send! (message channel :datsync.remote/tx)"
  [conn tx]
  (println "Sending tx:" tx)
  ;; XXX Need to make datomic-tx more a multimethod on op, so we can properly translate custom txs
  (let [datomic-tx (datsync/datomic-tx conn tx)]
    ;(js/console.log "Tx translation for datomic: " (pr-str datomic-tx))
    (ws/chsk-send! [:datsync.remote/tx datomic-tx])))


(defn cast-value-type
  [value-type-ident str-value]
  (case value-type-ident
    (:db.type/double :db.type/float) (js/parseFloat str-value)
    (:db.type/long :db.type/integer) (js/parseInt str-value)
    str-value))


(defn make-change-handler
  [conn e attr-ident old-value]
  ;; This whole business with the atom here is sloppy as hell... Will have to clean up with smarter delta
  ;; tracking in database... But for now...
  (let [current-value (r/atom old-value)
        value-type-ident (d/q '[:find ?value-type-ident .
                                :in $ % ?attr-ident
                                :where (attr-ident-value-type-ident ?attr-ident ?value-type-ident)]
                              @conn
                              rules
                              attr-ident)]
    (fn [new-value]
      (let [old-value @current-value
            new-value (cast-value-type value-type-ident new-value)]
        (reset! current-value new-value)
        (send-tx! conn (concat
                         (when old-value [[:db/retract e attr-ident old-value]])
                          ;; Probably need to cast, since this is in general a string so far
                         [[:db/add e attr-ident new-value]]))))))


(defn apply-reference-change!
  ([conn eid attr-ident new-value]
   (apply-reference-change! conn eid attr-ident nil new-value))
  ([conn eid attr-ident old-value new-value]
   (send-tx! conn (concat [[:db/add eid attr-ident new-value]]
                          (when old-value
                            [[:db/retract eid attr-ident old-value]])))))


(defn select-entity-input
  {:todo ["Finish..."
          "Create some attribute indicating what entity types are possible values; other rules?"]}
  ([conn eid attr-ident value]
   (let [options (->>
                   @(posh/q conn
                            '[:find [(pull ?eid [*]) ...]
                              :in $ ?attr-ident
                              :where [?attr :db/ident ?attr-ident]
                                     [?attr :attribute.ref/types ?type]
                                     [?type :db/ident ?type-ident]
                                     [?eid :e/type ?type-ident]
                                     ]
                            attr-ident)
                   ;; XXX Oh... should we call entity-name entity-label? Since we're really using as the label
                   ;; here?
                   (mapv (fn [entity] (assoc entity :label (entity-name entity)
                                                    :id (:db/id entity))))
                   (sort-by :label))]
     [select-entity-input conn eid attr-ident value options]))
  ([conn eid attr-ident value options]
   [re-com/single-dropdown
    :style {:min-width "150px"}
    :choices options
    :model value
    :on-change (partial apply-reference-change! conn eid attr-ident value)]))


;; Simple md (markdown) component; Not sure if we really need to include this in datview or not...
(defn md
  [md-string]
  [re-com/v-box
   :children
   [[:div {:dangerouslySetInnerHTML {:__html (md/md->html md-string)}}]]])


;; ### Datetimes...

;; XXX Need to get proper dat+time handlers

;(defn update-date
  ;[old-instant new-date]
  ;;; For now...
  ;(let [old-instant (cljs-time.coerce/from-date old-instant)
        ;day-time (cljs-time/minus old-instant
                                  ;(cljs-time/at-midnight old-instant))
        ;new-time (cljs-time/plus new-date
                                 ;day-time)]
    ;new-time
    ;))

(defn update-date
  [old-instant new-date]
  ;; For now...
  new-date)

(defn datetime-date-change-handler
  [conn eid attr-ident current-value new-date-value]
  (let [old-value @current-value
        new-value (update-date old-value new-date-value)]
    (reset! current-value new-value)
    (send-tx! conn
              (concat (when old-value
                        [[:db/retract eid attr-ident (cljs-time.coerce/to-date old-value)]])
                      [[:db/add eid attr-ident (cljs-time.coerce/to-date new-value)]]))))

;; XXX Finish
(defn datetime-time-change-handler
  [conn eid attr-ident current-value new-time-value]
  ())

(defn timeint-from-datetime
  [datetime]
  )

(defn datetime-selector
  [conn eid attr-ident value]
  (let [current-value (atom value)]
    (fn []
      [:datetime-selector
       [re-com/datepicker-dropdown :model (cljs-time.coerce/from-date (or @current-value (cljs-time/now)))
                                   :on-change (partial datetime-date-change-handler conn eid attr-ident current-value)]
       ;[re-com/input-time :model (timeint-from-datetime @current-value)
                          ;:on-change (partial datetime-time-change-handler conn eid attr-ident current-value)]
       ])))

(defn boolean-selector
  [conn eid attr-ident value]
  (let [current-value (atom value)]
    (fn []
      [re-com/checkbox :model @current-value
                       :on-change (fn [new-value]
                                    (let [old-value @current-value]
                                      (reset! current-value new-value)
                                      (send-tx! conn (concat
                                                       (when-not (nil? old-value)
                                                         [[:db/retract eid attr-ident old-value]])
                                                       [[:db/add eid attr-ident new-value]]))))])))

(defn input-for
  ([conn eid attr-ident value]
   (when-let [attr-entity @(posh/pull conn
                                      '[:db/isComponent {:db/valueType [:db/ident]}]
                                      [:db/ident attr-ident])]
     (match [attr-entity]
       ;; We have an isComponent ref; do nested form
       [{:db/valueType {:db/ident :db.type/ref} :db/isComponent true}]
       [edit-entity-fieldset conn value])
       ;; Non component entity; Do dropdown select...
       [{:db/valueType {:db/ident :db.type/ref}}]
       [select-entity-input conn eid attr-ident value]
       ;; Need separate handling of datetimes
       [{:db/valueType {:db/ident :db.type/instant}}]
       [datetime-selector conn eid attr-ident value]
       ;; Booleans should be check boxes
       [{:db/valueType {:db/ident :db.type/boolean}}]
       [boolean-selector conn eid attr-ident value]
       ;; For numeric inputs, want to style a little differently
       [{:db/valueType {:db/ident (:or :db.type/float :db.type/double :db.type/integer :db.type/long)}}]
       [re-com/input-text
        :model (str value)
        :width "130px"
        :on-change (make-change-handler conn eid attr-ident value)]
       ;; Misc; Simple input, but maybe do a dynamic type dispatch as well for customization...
       :else
       [re-com/input-text
        :model (str value) ;; just to make sure...
        :width (if (= attr-ident :db/doc) "350px" "200px")
        :on-change (make-change-handler conn eid attr-ident value)]))))


(defn create-type-reference
  [conn eid attr-ident type-ident]
  (send-tx! conn
          ;; Right now this also only works for isComponent :db.cardinality/many attributes. Should
          ;; generalize for :db/isComponent false so you could add a non-ref attribute on the fly XXX
          [{:db/id -1 :e/type type-ident}
           [:db/add eid attr-ident -1]]))


;; XXX Again; should maybe switch to just eid, and let user pass in [:db/ident attr-ident]
(defn attr-ident-types
  [conn attr-ident]
  (posh/q conn
          '[:find [?type-ident ...]
            :in $ % ?attr-ident
            :where (attr-ident-type-ident ?attr-ident ?type-ident)]
          rules
          attr-ident))


(defn attr-type-selector
  [type-idents selected-type ok-fn cancel-fn]
  ;; Right now only supports one; need to make a popover or something that asks you what type you want to
  ;; create if there are many possible... XXX
  [re-com/v-box
   ;:style {:width "500px" :height "300px"}
   :children
   [[re-com/title :label "Please select an entity type"]
    [re-com/single-dropdown
     :choices (mapv (fn [x] {:id x :label (pr-str x)}) type-idents)
     :model selected-type
     :style {:width "300px"}
     :on-change (fn [x] (reset! selected-type x))]
    [re-com/h-box
     :children
     [[re-com/md-icon-button :md-icon-name "zmdi-check"
                             :size :larger
                             :style {:margin "10px"}
                             :tooltip "add selected entity"
                             :on-click ok-fn]
      [re-com/md-icon-button :md-icon-name "zmdi-close-circle"
                             :size :larger
                             :style {:margin "10px"}
                             :tooltip "Cancel"
                             :on-click cancel-fn]]]]])


;; All this skeleton stuff is a bit anoying; these things are what the user should be specifying, not the
;; other way around
(defn field-for-skeleton
  [conn attr-ident controls inputs]
  [re-com/v-box
   :style {:flex-flow "column wrap"}
   :padding "10px"
   :children
   [;; First the label view, and any label controls that might be needed
    [re-com/h-box
     :style {:flex-flow "row wrap"}
     :children
     [[label-view conn attr-ident]
      [re-com/h-box :children controls]]]
    ;; Put our inputs in a v-box
    [re-com/v-box
     :style {:flex-flow "column wrap"}
     :children inputs]]])

(defn add-reference-button
  "Simple add reference button"
  ([tooltip on-click-fn]
   [re-com/md-icon-button
    :md-icon-name "zmdi-plus"
    :on-click on-click-fn
    :tooltip tooltip])
  ([on-click-fn]
   (add-reference-button "Add entity" on-click-fn)))

;; Similarly, should have another function for doing the main simple operation here
(defn add-reference-for-type-button
  "Simply add a reference for a given type (TODO...)"
  [tooltip type-ident])

;; We should rewrite the main use case below to use this function istead of the one above; reduce complexity
(defn add-reference-button-modal
  "An add reference button that pops up a modal form with a submit button.
  modal-popup arg should be a component that takes param:
  * form-activated?: an atom with a bool indicating whether the form should be shown.
  This component should make sure to toggle form-activated? when it's done creating
  the component, or if there is a cancelation."
  ([tooltip modal-popup]
   (let [form-activated? (r/atom false)]
     (fn [tooltip modal-popup]
       [re-com/v-box
         :children
         [[add-reference-button tooltip (fn [] (reset! form-activated? true))]
          (when @form-activated?
            [re-com/modal-panel :child [modal-popup form-activated?]])]])))
  ([modal-popup]
   (add-reference-button "Add entity" modal-popup)))


;; Again; need to think about the right way to pass through the attribute data here
(defn field-for
  [conn eid attr-ident]
  ;; Should move all this local state in conn db if possible... XXX
  (let [activate-type-selector? (r/atom false)
        selected-type (r/atom nil)
        cancel-fn (fn []
                    (reset! activate-type-selector? false)
                    (reset! selected-type nil)
                    false)
        ok-fn (fn []
                (reset! activate-type-selector? false)
                (create-type-reference conn eid attr-ident @selected-type)
                (reset! selected-type nil)
                false)
        values (posh/q conn '[:find [?val ...]
                              :in $ ?eid ?attr-ident
                              :where [?eid ?attr-ident ?val]]
                            eid
                            attr-ident)]
        ;; XXX Need to add sorting functionality here...
    (fn [conn eid attr-ident]
      ;; Ug... can't get around having to duplicate :field and label-view
      ;(when @(posh/q conn '[:find ?eid :in $ ?eid :where [?eid]]) ...)
        (let [card @(posh/q conn
                            '[:find (pull ?card [*]) .
                              :in $ ?attr-ident
                              :where [?attr :db/ident ?attr-ident]
                                     [?attr :db/cardinality ?card]]
                            attr-ident)
              type-idents @(attr-ident-types conn attr-ident)]
          [field-for-skeleton conn attr-ident 
            ;; Right now these can't "move" because they don't have keys XXX Should fix with another component
            ;; nesting...
            [^{:key (hash :add-reference-button)}
             (when (and card (= :db.cardinality/many (:db/ident card)))
               [add-reference-button (fn []
                                       (cond
                                         (> (count type-idents) 1)
                                         (reset! activate-type-selector? true)
                                         :else 
                                         (create-type-reference conn eid attr-ident (first type-idents))))])
             ;; Need a flexible way of specifying which attributes need special functions associated in form
             ^{:key (hash :attr-type-selector)}
             (when @activate-type-selector?
               [re-com/modal-panel
                :child [attr-type-selector type-idents selected-type ok-fn cancel-fn]])]
            ;; Then for the actual values
            (for [value (or (seq @values) [nil])]
              ^{:key (hash {:component :field-for :eid eid :attr-ident attr-ident :value value})}
              [input-for conn eid attr-ident value])]))))


;; Ugh... this is terrible; need to replace the other ordered-attributes with this one, since I think this one
;; is better written and a little more extensible
;; Umm... I may have done this to some extent already; look at ordered-attributes above
;; One way or the other need to standardize
(defn ordered-attributes2
  [conn eid]
  (let [grouped-attr-idents (group-by (fn [[attr-type-ident iscomp _]]
                                        (if (= attr-type-ident :db.type/ref)
                                          [:db.type/ref iscomp]
                                          :other))
                                      @(signed-attributes conn eid))]
    (->> (concat (grouped-attr-idents :other)
                 (grouped-attr-idents [:db.type/ref false])
                 (grouped-attr-idents [:db.type/ref true]))
         (map last)
         (filter visible-attribute?))))


(defn get-remote-eid
  [conn eid]
  (:datsync.remote.db/id (d/pull @conn [:datsync.remote.db/id] eid)))

(defn delete-entity-handler
  [conn eid]
  (when (js/confirm "Delete entity?")
    (let [entity (d/pull @conn [:e/type :datsync.remote.db/id] eid)]
      (println (str "Deleting entity: " eid))
      (match [entity]
        ;; may need the ability to dispatch in here;
        :else
        (send-tx! conn [[:db.fn/retractEntity eid]])))))


;; Let's do a thing where we have 
(defn edit-entity-fieldset-skeleton
  [conn eid children]
  [re-com/v-box
   :style {:border "3px solid grey"
           :background styles/light-grey
           :flex-flow "column wrap"
           :padding "10px"
           :margin "10px"}
   :children [[re-com/h-box
               :gap "8px"
               ;:justify :end
               :style {:padding-bottom "5px"}
               :children [[entity-summary conn eid]
                          [re-com/gap :size "1"]
                          ;; Add copy entity? Flexible way of adding other controls?
                          [re-com/md-icon-button :md-icon-name "zmdi-close-circle"
                                                 :on-click (partial delete-entity-handler conn eid)
                                                 :tooltip "Delete entity"]]]
              ;; Need to sort by attribute type and is component (and sortables) XXX
              [re-com/v-box
               :style {:flex-flow "column wrap"}
               :children children]]])


(defn edit-entity-fieldset
  [conn eid]
  (if-let [eid (and eid @(posh/q conn '[:find ?eid . :in $ ?eid :where [?eid]] eid))]
    ;; This admittedly looks a little hacky... And we aren't even taking order into consideration yet. Will
    ;; really need to clean all this up into something that can be shared by the view and edit components XXX
    (let [grouped-attr-idents (group-by (fn [[attr-type-ident iscomp _]]
                                          (if (= attr-type-ident :db.type/ref)
                                            [:db.type/ref iscomp]
                                            :other))
                                        @(signed-attributes conn eid))
          main-attributes (->> (concat (grouped-attr-idents :other)
                                       (grouped-attr-idents [:db.type/ref false]))
                               (map last)
                               (filter visible-attribute?))
          component-attributes (->> (grouped-attr-idents [:db.type/ref true]) 
                                    (map last)
                                    (filter visible-attribute?))]
      ;; Not actually using fieldsets because they don't seem to let us apply flexbox styling.
      (let [fields-for (fn [attr-idents]
                         [re-com/h-box
                          :gap "10px"
                          :style {:flex-flow "row wrap"}
                          :children
                          (for [attr-ident attr-idents]
                            ^{:key (hash {:attr-ident attr-ident})}
                            [field-for conn eid attr-ident])])]
        ;; Not sure if I want to keep these things separate like this and forgo the fields-for fn above
        ;; May help fix bug
        [edit-entity-fieldset-skeleton conn eid
         [[re-com/h-box
           :gap "10px"
           :style {:flex-flow "row wrap"}
           :children (for [attr-ident main-attributes]
                       ^{:key (hash {:attr-ident attr-ident})}
                       [field-for conn eid attr-ident])]
          [re-com/v-box
           :gap "10px"
           :style {:flex-flow "column wrap"}
           :children (for [attr-ident component-attributes]
                       ^{:key (hash {:attr-ident attr-ident})}
                       [field-for conn eid attr-ident])]]]))))


(defn loading-notification
  [message]
  [re-com/v-box
   :style {:align-items "center"
           :justify-content "center"}
   :gap "15px"
   :children
   [[re-com/title :label message]
    [re-com/throbber :size :large]]])


(defn edit-entity-form
  [conn remote-eid]
  (if-let [eid @(posh/q conn '[:find ?e . :in $ ?remote-eid :where [?e :datsync.remote.db/id ?remote-eid]] remote-eid)]
    [re-com/v-box :children [[edit-entity-fieldset conn eid]]]
    [loading-notification "Please wait; the entity is loading."]))


