(ns dat.sys.figwheel-server
  (:require [com.stuartsierra.component :as component]
            [figwheel-sidecar.system :as figwheel]
            [dat.sys.shared.utils :refer [deep-merge]]
            ))

(defrecord FigwheelServer [config ring-routes figwheel-system]
  component/Lifecycle
  (start [component]
         (let [port (-> config :server :port)
               fig-config (deep-merge (figwheel/fetch-config)
                                      {:figwheel-options (merge
                                                           {:ring-handler (:ring-handler ring-routes)}

                                                           ;; comment out to allow figwheel config to determine the port instead of datsys config:
                                                           (when port {:server-port port})
                                                           )})
               figwheel-system (figwheel/figwheel-system fig-config)]
           (component/start figwheel-system)
           (assoc component :figwheel-system figwheel-system)))
  (stop [component]
         (component/stop figwheel-system)
         (assoc component :figwheel-system nil)))

(defn browser-repl [fig-server]
  (figwheel/cljs-repl (:figwheel-system fig-server) nil))

(defn new-figwheel-server []
  (map->FigwheelServer {}))
