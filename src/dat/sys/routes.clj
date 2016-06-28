(ns dat.sys.routes
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.resource :refer (wrap-resource)]
            [dat.sys.ws :as ws]))

(defn handler [ajax-post-fn ajax-get-or-ws-handshake-fn]
  (routes
   (GET  "/"     _   (clojure.java.io/resource "index.html"))
   (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
   (POST "/chsk" req (ajax-post-fn req))
   (route/not-found "<h1>Page not found</h1>")))

(defn app [handler]
  (let [ring-defaults-config
        (-> site-defaults
            (assoc-in
             [:security :anti-forgery]
             {:read-token (fn [req] (-> req :params :csrf-token))})
            (assoc-in [:static :resources] "public"))]
    (-> handler
        (wrap-defaults ring-defaults-config)
        (wrap-resource "/META-INF/resources"))))


(defrecord RingRoutes [config ws-connection ring-handler]
  component/Lifecycle
  (start [component]
      component
      (let [component (component/stop component)
            {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} (ws/ring-handlers ws-connection)
            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn)]
        (assoc component :ring-handler (app handler))))
  (stop [component]
    (assoc component :ring-handler nil)))


(defn new-ring-routes []
  (map->RingRoutes {}))

