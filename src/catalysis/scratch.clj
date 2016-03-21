(ns catalysis.scratch)

(comment
  (require '[cemerick.pomegranate :refer [add-dependencies]])
  (defn load-dep
    [dep]
    (add-dependencies :coordinates [dep] :repositories (merge cemerick.pomegranate.aether/maven-central {"clojars" "http://clojars.org/repo"})))
  ;(load-dep '[clj-time "0.10.0"])
  (load-dep '[io.rkn/conformity "0.4.0"])
  )
