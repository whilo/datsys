(ns catalysis.client.views
  (:require [catalysis.client.ws :as socket]))


;; Rewrite in terms of posh XXX
(defn tasks-view
  [tasks-data]
  [:div
   [:h2 "Todo"]
   [:table
    [:tr [:th "Task"] [:th "Tags"]]
    (for [task (tasks-data)]
      [:tr
       [:td (:e/name task)]
       [:td (for [tag (:e/tags task)]
              (tag-view tag))]])]])
   

(defn main [conn]
  [:div
   [:h1 "Catalysis"]
   [:p "Congrats! You've got a catalysis app running :-)"]])

