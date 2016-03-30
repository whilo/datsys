(ns catalysis.datomic
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [clojure.java.io :as io]
            [io.rkn.conformity :as conformity]
            [com.stuartsierra.component :as component]))

;; Will need to come up with a migration system XXX
;; Look at https://github.com/rkneufeld/conformity and https://github.com/bitemyapp/brambling
;; Not hard to bake our own as well if those don't work
(defn ensure-schema!
  [conn]
  ;; The schema is in `resources/schema.edn`
  (let [schema-data (-> "schema.edn" io/resource slurp read-string)]
    ;; This is where ideally we would be looking at a dependency graph of norms and executing in that order.
    ;; Look at Stuart Sierra's dependency library. XXX
    (doseq [k (keys schema-data)]
      (conformity/ensure-conforms conn schema-data [k]))))

;(-> "config/local/seed-data.edn" slurp read-string)

(defn load-data!
  [conn filename]
  (let [data (-> filename slurp read-string)]
    (d/transact conn data)))

(defrecord Datomic [config conn]
  component/Lifecycle
  (start [component]
    (let [url (-> config :datomic :url)
          created? (d/create-database url)
          conn (d/connect url)]
      ;; XXX Should be a little smarter here and actually test to see if the schema is in place, then transact
      ;; if it isn't. Similarly when we get more robust migrations.
      (log/info "Datomic Starting")
      (ensure-schema! conn)
      (when-let [seed-data-filename (-> config :datomic :seed-data)]
        (load-data! conn seed-data-filename))
      (assoc component :conn conn)))
  (stop [component]
    (d/release conn)
    (assoc component :conn nil)))

(defn create-datomic []
  (map->Datomic {}))

(defn delete-database!
  [component]
  (-> component :config :datomic :url d/delete-database))

;; Add basic datomic logic here TODO
(comment
  (require 'user)
  (def datomic (-> user/system :datomic))
  (println "there are" (count (d/history (d/db (:conn datomic)))))
  (d/transact (-> datomic :conn)
              (-> "config/local/seed-data.edn" slurp read-string))
  (d/q '[:find (pull ?e)
         :where [?t :e/name "polis"]
                [?e :e/tags ?t]
                [?e :e/name ?ename]
         ] (-> datomic :conn d/db))

  )


