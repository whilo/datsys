(ns datview.schema
  (:require [schema.core :as s :include-macros true]
            [reagent.ratom :as ratom]))


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

(def ControlsComponentFn s/Any)

(def ComponentAttrs
  (merge
    StrictComponentAttrs
    {(s/optional-key :component) GenericRenderFn
     (s/optional-key :controls) ControlsComponentFn
     (s/optional-key :wrapper) WrapperFn
     s/Keyword s/Any}))

(def DatviewSpec
  "A schema for how things are supposed to be rendered, passed around as declarative query metadata."
  (merge
    {(s/optional-key :attributes) {s/Keyword ComponentAttrs}
     (s/optional-key :components) {s/Keyword GenericRenderFn}}
    ComponentAttrs))

;(println DatviewSpec)


;; Would be cool to have a function that computes a schema based on a particular pull expr (and perhaps current db val)
(s/defn PullDataForExpr :- s/Schema
  [db :- DataScriptDB, pull-expr :- PullExpr])

(def GenericPullData
  {s/Keyword s/Any})

;(def DataScriptDB
  ;(s/protocol ID))

;; directly use d/db?
;(def Conn
  ;(s/protocol)) 
(def Conn s/Any)

