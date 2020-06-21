(defproject jcpsantiago.controltower "1.5.1"
  :description "Slack app that tells you which flight is landing"
  :url "https://github.com/jcpsantiago/controltower"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[buddy/buddy-core "1.6.0"]
                 [cheshire "5.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.1"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.6.532"]
                 [org.clojure/data.json "0.2.7"]
                 [org.postgresql/postgresql "42.2.9"]
                 [hiccup "1.0.5"]
                 [http-kit "2.4.0-alpha6"]
                 [mock-clj "0.2.1"]
                 [proto-repl "0.3.1"]
                 [ring/ring-defaults "0.3.2"]
                 [seancorfield/next.jdbc "1.0.13"]]
  :plugins [[lein-cloverage "1.1.2"]
            [lein-cljfmt "0.6.6"]]
  :main ^:skip-aot controltower.core
  :min-lein-version "2.0.0"
  :uberjar-name "controltower.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
  ;:global-vars {*warn-on-reflection* true})
