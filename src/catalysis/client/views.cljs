(ns catalysis.client.views
  (:require [catalysis.client.ws :as socket]
            ))

;; TODO Build all views for the data here using posh queries

(defn tag-view
  [tag]
  [:a {:onclick nil} (:e/name tag)])

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
              (tag-view tag))]])]
   ])

(defn main [conn]
  (socket/chsk-send! [:catalysis/testevent {:message "please reply"}])
  [:div
   ;[:h1 (:title @data)]
   [:div "Hello world! This is reagent speaking!"]
   [:br]
   [:div "Look in your browsers developer console to see the web socket communication when clicking below buttons."]
   [:br]
   [:div conn]
   [:button {:on-click socket/test-socket-callback} "Send Message Callback"]
   ;[:button {:on-click #(c
   [:br]
   [:button {:on-click socket/test-socket-event} "Send Message Event"]])

