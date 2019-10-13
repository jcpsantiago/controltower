(defproject jcpsantiago.controltower "1.1.0"
  :description "Slack app that tells you which flight is landing"
  :url "https://github.com/jcpsantiago/controltower"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [http-kit "2.4.0-alpha3"]
                 [ring/ring-defaults "0.3.2"]
                 [org.clojure/data.json "0.2.6"]
                 [proto-repl "0.3.1"]
                 [org.clojure/core.async "0.4.500"]
                 [cheshire "5.8.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [clojure2d "1.2.0-SNAPSHOT"]]
  :plugins [[lein-cloverage "1.1.1"]]
  :main ^:skip-aot controltower.core
  :min-lein-version "2.0.0"
  :uberjar-name "controltower.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
