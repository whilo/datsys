(ns catalysis.client.views
  "# Views"
  (:require [catalysis.client.ws :as ws]
            [datview.core :as datview]
            [datview.forms :as forms]
            [posh.core :as posh]
            [reagent.core :as r]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame] 
            [clojure.string :as string]
            [datascript.core :as d]
            [cljs.reader :as reader]))


;; The core views namespace...

;; We'll be looking at how you might set up a simple Todo application using Datsync, DataScript, Posh and
;; Datview.


;; ## Everything's data

;; Datview translates declarative queries into virtual dom (via hiccup).
;; These declarative queries have embedded in them not just the "data" query (as we typically think of it in
;; database terms), but also a description of how to translate the query results into hiccup.
;; These descriptions are stored as metadata on DataScript query data structures.

;; The simplest of examples:

(def time-entry
  [:e/name :time.entry/start-time :time.entry/stop-time :e/description])

(def base-todo-view
  ^{:attributes {:attr-view {:style {:background-color ""}}}
    :summary datview/pull-summary-view}
  ;; You can also plug in a reactive atom if you wish
  ;^{:datview/spec spec-atom}
  [:db/id :e/name :e/description
   {:e/category [:e/name :e/description]
    :e/tags [:e/name :e/description]
    :todo/time-entries time-entry}])

;; We could call (datview/pull-view conn base-todo-view eid) and get a hiccup view of the 

;; We should just be able to use recursion depth specifications and ...; Posh doesn't allow, so this is just a
;; hack for now...
(defn todo-view
  ([]
   base-todo-view)
  ([depth]
   (if-not (zero? depth)
     (conj base-todo-view {:todo/subtasks (todo-view (dec depth))})
     base-todo-view)))

;; The above only describes how we'd render a single todo item.
;; But we'll want to render a collection.

;; (Soon this will be possible by using annotated pull directly within `q`, but for now you're stuck
;; separating things out this way)

(defn type-instance-eids-rx
  [conn type-ident]
  (posh/q conn '[:find [?e ...] :in $ ?type-ident :where [?e :e/type ?type-ident]] [:db/ident type-ident]))

;; Now we can put these things together into a Reagent component

(defn todos-view [conn]
  (let [todo-eids @(type-instance-eids-rx conn :e.type/Todo)]
    [re-com/v-box
     :margin "20px 5px 5px"
     :children [[:h2 "Todos"]
                [:p "Below are forms and views for each todo item in the database"]
                (for [todo todo-eids]
                  ^{:key todo}
                  [:div {:style {:margin "20px 5px"}}
                   ;; Should be using shared reaction here?
                   [forms/pull-form conn (todo-view 1) todo]
                   [datview/pull-view conn (todo-view 1) todo]])]]))

(defn main [conn]
  [re-com/v-box
   :margin "15px"
   :gap "15px"
   :children [[:h1 "Catalysis"]
              [:p {:style {:font-size "18px"}} "Congrats! You've got a catalysis app running :-)"]
              ;[datview/debug "Here's a debug example:"
               ;@(type-instance-eids-rx conn :e.type/Todo)]
              [todos-view conn]]])


;; ## Crazy/cool ideas:

;; * Learn a datomic database schema by scanning through data and building type inferences...


