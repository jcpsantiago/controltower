(defproject jcpsantiago.controltower "1.3.0"
  :description "Slack app that tells you which flight is landing"
  :url "https://github.com/jcpsantiago/controltower"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[cheshire "5.8.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.1"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.500"]
                 [org.clojure/data.json "0.2.6"]
                 [org.postgresql/postgresql "42.2.6"]
                 [hiccup "1.0.5"]
                 [http-kit "2.4.0-alpha3"]
                 [mock-clj "0.2.1"]
                 [proto-repl "0.3.1"]
                 [ring/ring-defaults "0.3.2"]
                 [seancorfield/next.jdbc "1.0.9"]]
  :plugins [[lein-cloverage "1.1.1"]
            [lein-cljfmt "0.6.4"]]
  :main ^:skip-aot controltower.core
  :min-lein-version "2.0.0"
  :uberjar-name "controltower.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
