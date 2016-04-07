(ns catalysis.client.views
  (:require [catalysis.client.ws :as ws]
            [posh.core :as posh]
            [clojure.string :as string]
            [datascript.core :as d]
            [cljs.reader :as reader]))

;; TODO build application view here

(defn main [conn]
  [:div
   [:h1 "Catalysis"]
   [:debug (reduce (fn [base [elem]] (str base elem " <br> ")) " " @(posh/q conn '[:find ?e :where [_ :e/name ?e]]))]
   [:p "Congrats! You've got a catalysis app running :-)"]
   [:textarea {:id "req-box" :rows 1 :columns 150}]
   [:br]
   [:button {:onClick (fn []
                        (let [text-request (.-value (.getElementById js/document "req-box"))]
                          (js/console.log text-request)
                          (one-time-tx conn (reader/read-string text-request))))}
    "Press to add"]
   ])

;[:datsync.client/tx {:db/id -4 :e/type :e.type/Category :e/name "Play" :e/description "Fun things"}]
(defn process-request [conn]
  (let [text-request (.-innerHTML (js/getElementById "req-box"))]
    (js/console.log text-request)
    (one-time-tx conn (reader.read-string text-request))))

(defn one-time-tx [conn message]
    (js/console.log "sending message")
    (ws/send-tx! conn message))
