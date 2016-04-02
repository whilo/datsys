(ns catalysis.client.views
  (:require [catalysis.client.ws :as socket]))


;; TODO build application view here

(defn main [conn]
  [:div
   [:h1 "Catalysis"]
   [:p "Congrats! You've got a catalysis app running :-)"]])

