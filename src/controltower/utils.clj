(ns controltower.utils
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [clojure.string :as s])
  (:gen-class))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn parse-db-uri [uri] (drop 1 (s/split uri #"://|:|@|/")))

(defn replace-airline-icao
  [image-url icao-code]
  (s/replace image-url "_ICAO_" icao-code))

(defn create-map-from-uri
  [uri]
  (let [parsed (parse-db-uri uri)]
    (into {:dbtype "postgresql"}
          (zipmap [:user :password :host :port :dbname] parsed))))

(defn closest-int
  "Return a list of the n items of coll that are closest to x"
  [x n coll]
  (take n (sort-by #(Math/abs (- x %)) coll)))

(defn parse-json
  "Parse JSON into a map with keys"
  [file]
  (json/parsed-seq (clojure.java.io/reader file) true))

(defn parse-edn
  "Reads data saved as EDN"
  [file]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader file))]
    (binding [*read-eval* false] (read r))))

(defn log-http-status
  "Log API response"
  [{:keys [status body]} service type]
  (if (not (= status 200))
    (timbre/error "Failed, exception is" body)
    (timbre/info (str service " async HTTP " type " success: ") status)))

(defn night?
  "Determine if it's night or day based on openweather API response"
  [weather-response]
  (let [sys (:sys weather-response)
        localtime (:dt weather-response)
        sunrise (:sunrise sys)
        sunset (:sunset sys)]
    (or (< localtime sunrise) (> localtime sunset))))
