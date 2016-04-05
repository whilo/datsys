(ns catalysis.client.views
  (:require [catalysis.client.ws :as ws]
            [posh.core :as posh]
            [clojure.string :as string]
            [datascript.core :as d]))

;; TODO build application view here

(defn main [conn]
  [:div
   [:h1 "Catalysis"]
   [:debug (reduce (fn [base [elem]] (str base elem " <br> ")) " " @(posh/q conn '[:find ?e :where [_ :e/name ?e]]))]
   [:p "Congrats! You've got a catalysis app running :-)"]])

