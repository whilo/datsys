(ns dat.sys.server
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :refer (run-server)]))

(defrecord HttpServer [config ring-handler server-stop]
  component/Lifecycle
  (start [component]
    (if server-stop
      component
      (let [component (component/stop component)
            server-stop (run-server (:handler ring-handler) {:port (-> config :server :port)})]
        (log/info "HTTP server started")
        (assoc component :server-stop server-stop))))
  (stop [component]
    (when server-stop (server-stop))
    (log/debug "HTTP server stopped")
    (assoc component :server-stop nil)))


(defn new-http-server []
  (map->HttpServer {}))

