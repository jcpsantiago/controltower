(ns controltower.utils
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [taoensso.timbre :as timbre])
  (:gen-class))

(defn closest-int
  "Return a list of the n items of coll that are closest to x"
  [x n coll]
  (take n (sort-by #(Math/abs (- x %)) coll)))

(defn uuid
  "Create a UUID string"
  []
  (.toString (java.util.UUID/randomUUID)))

(defn add-uuid
  "Add UUID to filename path with file extension"
  [string uuid extension]
  (str string uuid extension))

(defn parse-json
  "Parse JSON into a map with keys"
  [file]
  (json/parsed-seq (clojure.java.io/reader file)
                   true))

(defn log-http-status
  "Log API response"
  [{:keys [status body]} service type]
  (if (not (= status 200))
    (timbre/error "Failed, exception is" body)
    (timbre/info (str service " async HTTP " type " success: ") status)))
