(ns catalysis.client.views
  (:require [catalysis.client.ws :as ws]
            [posh.core :as posh]
            [clojure.string :as string]
            [datascript.core :as d]
            [cljs.reader :as reader]))


;; TODO Replace this with your actual view code!


Write a simple todo app using the datview tooling

(defn main [conn]
  [:div
   [:h1 "Catalysis"]
   [:p "Congrats! You've got a catalysis app running :-)"]
   [:textarea {:id "req-box" :rows 1 :columns 150}]
   [:br]
   [:button {:onClick (fn []
                        (let [text-request (.-value (.getElementById js/document "req-box"))]
                          (js/console.log text-request)
                          (ws/send-tx! conn (reader/read-string text-request))))}
    "Press to add"]])
   

