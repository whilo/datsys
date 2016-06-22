(defproject datsys "1.0.0"
  :description "(+ clj cljs datomic datascript frp) web development framework" ;;should this be "an" or "un"?
  :url "https://github.com/metasoarous/datsys"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha7"]
                 [org.clojure/clojurescript "1.9.76"]
                 [org.clojure/core.async "0.2.382"]
                 [org.clojure/tools.logging "0.3.1"] ;; Should remove this for timbre
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.typed "0.3.23"]
                 ;; Datsys things
                 [datsync "0.0.1-alpha1-SNAPSHOT"]
                 [datview "0.0.1-alpha1-SNAPSHOT"]
                 [datspec "0.0.1-alpha1-SNAPSHOT"]
                 [datreactor "0.0.1-alpha1-SNAPSHOT"]
                 ;; Other stuff (should try to clean things up once in main project)
                 [com.stuartsierra/component "0.3.1"]
                 [environ "1.0.3"]
                 [slingshot "0.12.2"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [compojure "1.5.0"]
                 [http-kit "2.1.19"]
                 [bidi "2.0.9"]
                 [com.cognitect/transit-clj "0.8.285" :exclusions [commons-codec]]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [testdouble/clojurescript.csv "0.2.0"]
                 [datascript "0.15.0"]
                 [posh "0.3.5"]
                 [reagent "0.6.0-rc"]
                 [re-frame "0.7.0"]
                 [org.webjars/bootstrap "3.3.5"]
                 [re-com "0.8.3"]
                 [prismatic/schema "1.1.2"]
                 [io.rkn/conformity "0.4.0"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [com.taoensso/timbre "4.4.0"]
                 [com.taoensso/encore "2.56.0"]
                 [com.taoensso/sente "1.8.1" :exclusions [org.clojure/tools.reader]]
                 ;;For the free version of Datomic
                 [com.datomic/datomic-free "0.9.5372" :exclusions [joda-time]]]
  :plugins [[lein-cljsbuild "1.1.1"]]
  ;; For Datomic Pro uncomment the following and set the DATOMIC_USERNAME and DATAOMIC_PASSWORD environment
  ;; variables of the process in which you run this program to those matching your Datomic Pro account. You'll
  ;; have to start your own transactor separately from this process as well. More instructions on how to do
  ;; that in the Wiki (I think... bug us if you can't find them).
  ;:repositories {"my.datomic.com" {:url
  ;                               "https://my.datomic.com/repo"
  ;                                 :username
  ;                                [:env/datomic_username]
  ;                                 :password
  ;                                 [:env/datomic_password]}}
  :source-paths ["src"]
  :resource-paths ["resources" "resources-index/prod"]
  :target-path "target/%s"
  :main ^:skip-aot dat.sys.run
  :repl-options {:init-ns user}
  :cljsbuild {:builds {:client {:source-paths ["src/dat/sys/client"
                                               "checkouts/datview/src"
                                               "checkouts/datsync/src"
                                               "checkouts/datreactor/src"
                                               "checkouts/datspec/src"]
                                :compiler {:output-to "resources/public/js/app.js"
                                           :output-dir "dev-resources/public/js/out"}}}}
                       ;:devcards {:source-paths ["src"]
                                  ;:figwheel {:devcards true}  ;; <- note this
                                  ;:compiler {:main    "dat.sys.client.cards"
                                             ;:asset-path "js/compiled/devcards_out"
                                             ;:output-to  "resources/public/js/datsys_devcards.js"
                                             ;:output-dir "resources/public/js/devcards_out"
                                             ;:source-map-timestamp true}}}}
  :figwheel {:server-port 3448
             :repl true}
  :profiles {:dev-config {}
             :dev [:dev-config
                   {:dependencies [[alembic "0.3.2"]
                                   [figwheel "0.5.4-3"]
                                   [devcards "0.2.1"]]
                    :plugins [[lein-figwheel "0.5.4-3" :exclusions [org.clojure/clojure org.clojure/clojurescript org.codehaus.plexus/plexus-utils]]
                              [com.palletops/lein-shorthand "0.4.0"]
                              [lein-environ "1.0.1"]]
                    ;; The lein-shorthand plugin gives us access to the following shortcuts as `./*` (e.g. `./pprint`)
                    :shorthand {. [clojure.pprint/pprint
                                   alembic.still/distill
                                   alembic.still/lein
                                   taoensso.timbre/trace
                                   taoensso.timbre/spy]}
                    :source-paths ["dev"]
                    ;; libs/datsync/resources is important here; It's lib code need access to it's resources
                    ;; dir in dev
                    :resource-paths ^:replace ["resources" "libs/datsync/resources" "dev-resources" "resources-index/dev"]
                    :cljsbuild
                    {:builds
                     {:client {:source-paths ["dev"]
                               :compiler
                               {:optimizations :none
                                :source-map true}}}}}]
             :prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :pretty-print false}}}}}}
  :aliases {"package"
            ["with-profile" "prod" "do"
             "clean" ["cljsbuild" "once"]]})

