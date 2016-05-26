(ns datview.forms
  "# Datview forms"
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [posh.core :as posh]
            [datview.router :as router]
            [datview.core :as datview]
            [datview.old :as old]
            [catalysis.client.ws :as ws]
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



(declare edit-entity-fieldset)

;; TODO XXX Not sure yet how to abstract around this; general event handlers probably
;; Must solve before abstraction
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
  "Takes a conn, an eid attr-ident and an old value, and builds a change handler for that value"
  [conn eid attr-ident old-value]
  ;; This whole business with the atom here is sloppy as hell... Will have to clean up with smarter delta
  ;; tracking in database... But for now...
  (let [current-value (r/atom old-value)
        value-type-ident (d/q '[:find ?value-type-ident .
                                :in $ % ?attr-ident
                                :where (attr-ident-value-type-ident ?attr-ident ?value-type-ident)]
                              @conn
                              old/rules
                              attr-ident)]
    (fn [new-value]
      (let [old-value @current-value
            new-value (cast-value-type value-type-ident new-value)]
        (reset! current-value new-value)
        (send-tx! conn (concat
                         (when old-value [[:db/retract eid attr-ident old-value]])
                          ;; Probably need to cast, since this is in general a string so far
                         [[:db/add eid attr-ident new-value]]))))))


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
                                     [?eid :e/type ?type-ident]]
                            attr-ident)
                   ;; XXX Oh... should we call entity-name entity-label? Since we're really using as the label
                   ;; here?
                   (mapv (fn [entity] (assoc entity :label (datview/pull-summary entity)
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
;(defn md
  ;[md-string]
  ;[re-com/v-box
   ;:children
   ;[[:div {:dangerouslySetInnerHTML {:__html (md/md->html md-string)}}]]])


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
   ;; XXX TODO Need to base this on the generalized stuff
   (when-let [attr @(datview/attribute-signature-reaction conn attr-ident)]
     (match [attr]
       ;; We have an isComponent ref; do nested form
       [{:db/valueType :db.type/ref :db/isComponent true}]
       [edit-entity-fieldset conn value]
       ;; Non component entity; Do dropdown select...
       [{:db/valueType :db.type/ref}]
       [select-entity-input conn eid attr-ident value]
       ;; Need separate handling of datetimes
       [{:db/valueType :db.type/instant}]
       [datetime-selector conn eid attr-ident value]
       ;; Booleans should be check boxes
       [{:db/valueType :db.type/boolean}]
       [boolean-selector conn eid attr-ident value]
       ;; For numeric inputs, want to style a little differently
       [{:db/valueType (:or :db.type/float :db.type/double :db.type/integer :db.type/long)}]
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
          old/rules
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
     [[datview/label-view conn attr-ident]
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
  [conn pull-expr eid attr-ident value]
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
        config (datview/component-config)]
        ;; XXX Need to add sorting functionality here...
    (fn [conn pull-expr eid attr-ident value]
      ;; Ug... can't get around having to duplicate :field and label-view
      ;(when @(posh/q conn '[:find ?eid :in $ ?eid :where [?eid]]) ...)
        (let [card @(posh/q conn
                            '[:find (pull ?card [*]) .
                              :in $ ?attr-ident
                              :where [?attr :db/ident ?attr-ident]
                                     [?attr :db/cardinality ?card]]
                            attr-ident)
              type-idents @(attr-ident-types conn attr-ident)]
          [:div (get-in @config [:attributes :field-for])
           [:div (get-in @config [:controls])]]
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
           ;:background styles/light-grey ;; Should either put styles in the db, or pass as args...
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


(defn pull-data-form
  ;; Need a config option here for whether state mode should be auto-update or form based.
  [conn pull-expr data]
  ;; This eid check was kinda a hack... should see if I can find a way around it
  (if-let [eid (and eid @(posh/q conn '[:find ?eid . :in $ ?eid :where [?eid]] (:db/id eid)))]
    ;; Not actually using fieldsets because they don't seem to let us apply flexbox styling.
    (let [config (component-config conn (meta pull-expr))]
      [:div (get-in config [:attributes :pull-data-form])
       (for [attr-spec pull-expr]
         ^{:key (hash attr-spec)}
         [field-for conn pull-expr eid attr-ident])])))

(defn loading-notification
  [message]
  [re-com/v-box
   :style {:align-items "center"
           :justify-content "center"}
   :gap "15px"
   :children
   [[re-com/title :label message]
    [re-com/throbber :size :large]]])

(defn pull-form
  [conn pull-expr eid]
  (if-let [current-data (posh/pull conn pull-expr eid)]
    [pull-data-form conn pull-expr current-data]
    [loading-notification "Please wait; loading data."]))



(defn edit-entity-form
  [conn remote-eid]
  (if-let [eid @(posh/q conn '[:find ?e . :in $ ?remote-eid :where [?e :datsync.remote.db/id ?remote-eid]] remote-eid)]
    [re-com/v-box :children [[edit-entity-fieldset conn eid]]]
    [loading-notification "Please wait; the entity is loading."]))


;; These are our new goals

(defn pull-data-form
  [conn pull-expr eid]
  (if-let [current-data @(posh/pull conn pull-expr eid)]
    [re-com/v-box :children [[edit-entity-fieldset conn eid]]]
    [loading-notification "Please wait; loading data."]))

(defn pull-form
  [conn pull-expr eid])


