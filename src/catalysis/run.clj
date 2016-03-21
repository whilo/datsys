(ns catalysis.run
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [catalysis.config :as config]
            [catalysis.system :refer [create-system]]))

(defn -main [& args]
  ;; TODO Eventually hook command line args into config-overwrides here so they flow through system. Room for
  ;; lib work...
  (component/start (create-system))
  (log/info "catalysis started"))

