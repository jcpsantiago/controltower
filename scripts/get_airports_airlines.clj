#!/usr/bin/env bb

(ns jcpsantiago.controltower.scripts.airportsscript
  (:require [babashka.curl :as curl]
            [clojure.string :as s]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [babashka.pods :as pods]))


(pods/load-pod "bootleg")
(require '[pod.retrogradeorbit.bootleg.utils :refer [convert-to]]
         '[pod.retrogradeorbit.hickory.select :as hs])


;; Airline data ;;
(def wiki-airlines 
  (-> "https://en.wikipedia.org/wiki/List_of_airline_codes"
      curl/get
      :body))


; https://gist.github.com/jackrusher/97734e71bb748ed9c263d6e3daea2b38
(defn deepest-text
  "Drill down to the deepest text node(s) and return them as a string."
  [node]
  (cond (vector? node) (apply str (mapcat deepest-text node))
        (map? node) (deepest-text (:content node))
        :else node))


; https://gist.github.com/jackrusher/97734e71bb748ed9c263d6e3daea2b38
(defn extract-tables 
  "Takes an html page and extracts a table element."
  [html]
  (mapv (fn [table]
          (mapv #(mapv deepest-text
                       (hs/select (hs/or (hs/tag :th) (hs/tag :td)) %))
                (hs/select (hs/tag :tr) table)))
        (->> (convert-to html :hickory) 
             (hs/select (hs/tag :table)))))


(defn to-keyword
  "This takes a string and returns a normalized keyword."
  [input]
  (-> input
      s/lower-case
      (s/replace \space \-)
      ; critical! there are some hidden characters that mess up
      ; subestting later
      s/trim
      keyword))


(defn html->map 
  "Extracts an html table and turns it into a map."
  [wiki-airlines] 
  (let [table (first (extract-tables wiki-airlines))
        headers (->> table
                     first
                     (map to-keyword)
                     vec)
        rows (->> table
                  (drop 1)
                  (mapv #(mapv s/trim-newline %)))]
    (println "Joining rows and headers...")
    (mapv #(zipmap headers %) rows)))


(defn complete?
  [k coll]
  (every? true? (map not [(= (k coll) "\n")
                          (= (k coll) "n/a")
                          (= (k coll) "")])))


(defn not-defunct?
  [coll]
  (if (seq (:comments coll))
    (let [c (s/lower-case (:comments coll))]
      (not (s/includes? c "defunct")))
  ; FIXME: some rows don't have a comments td ¯\_(ツ)_/¯
  ; I'll consider these as not defunct for now
    true))


(def complete-icao? (partial complete? :icao))
(def complete-iata? (partial complete? :iata))


(defn airline-code-map
  [k airline-map]
  (let [code (->> airline-map
                  (mapv #(keyword (s/lower-case (k %)))))]
    (zipmap code airline-map)))


(println "Collecting Airline ICAO codes")
(->> (html->map wiki-airlines)
     (filter (every-pred complete-iata? complete-icao? not-defunct?)) 
     (airline-code-map :icao)
     pr-str
     (spit "../resources/airlines_icao.edn"))


;; Airport data ;;
(defn all-airports []
  (println "Getting airport data from ourairports.com...")
  (-> "https://ourairports.com/data/airports.csv"
      curl/get
      :body))


(defn csv-data->maps [csv-data]
  (println "Parsing csv into maps...")
  (map zipmap
       (->> (first csv-data) 
            (map keyword) 
            repeat)
    (rest csv-data)))


(defn airports-to-keep [airports-map] 
  (println "Keeping airports with scheduled services...")
  (->> airports-map
       ; only scheduled flights for now so no helipads sorry : (
       (filter #(and (seq (:iata_code %)) 
                     (= (:scheduled_service %) "yes")))
       (pmap #(select-keys % [:type :name :latitude_deg :longitude_deg :elevation_ft
                              :iso_country :municipality :iata_code :ident]))
       (into [])))


;; https://stackoverflow.com/questions/12448629/create-a-bounding-box-around-the-geo-point
(defn calc-latitude-dif
  [radius]
  (*
   (Math/asin (/ radius 6378000))
   (/ 180 Math/PI)))


(defn calc-longitude-dif
  [latitude radius]
  (/ (->> (* Math/PI latitude)
          (/ 180)
          Math/cos
          (* 6378000)
          (/ radius)
          Math/asin
          (* 180))
     Math/PI))


(defn maxmin-lonlat
  [airport]
  (let [latitude (edn/read-string (:latitude_deg airport))
        longitude (edn/read-string (:longitude_deg airport))
        latitude-dif (calc-latitude-dif 5500)
        longitude-dif (calc-longitude-dif latitude 5500)
        latmax (+ latitude latitude-dif)
        lonmax (+ longitude longitude-dif)
        latmin (- latitude latitude-dif)
        lonmin (- longitude longitude-dif)]
     (conj airport [:boundingbox (str latmax "," latmin "," lonmin "," lonmax)])))


(defn checkpoint-bounding-boxes [x]
  (println "Generating bounding boxes...")
  x)


(->> (all-airports)
     csv/read-csv
     csv-data->maps
     airports-to-keep
     checkpoint-bounding-boxes
     (pmap maxmin-lonlat)
     (airline-code-map :iata_code)
     pr-str
     (spit "../resources/airports_with_boxes.edn"))


(println "All done!")
(println "OK Bye!")


(System/exit 0)
