(ns catalysis.client.views
  (:require [catalysis.client.ws :as socket]
            [posh.core :as posh]
            [clojure.string :as string]
            [datascript.core :as d]))
(def sent? (atom false))

(defn one-time-tx [conn]
;  (d/transact conn [{:db/id [:db.part/user -4]
;                     :e/type :e.type/Category
;                     :e/name "Play"
;                     :e/description "Fun things"}])
  (if sent?
    (js/console.log "message already sent")
    (do (socket/chsk-send! [:datsync.client/tx "[{:db/id #db/id[:db.part/user -4] :e/type :e.type/Category :e/name \"Play\" :e/description \"Fun things\"}]"])
      (swap! sent? true))))

;; TODO build application view here

(defn main [conn]
  (one-time-tx [conn])
  [:div
   [:h1 "Catalysis"]
   [:debug (reduce (fn [base [elem]] (str base elem " <br> ")) " " @(posh/q conn '[:find ?e :where [_ :e/name ?e]]))]
   [:p "Congrats! You've got a catalysis app running :-)"]])

