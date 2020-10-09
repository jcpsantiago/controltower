#!/usr/bin/env bb

(ns jcpsantiago.controltower.scripts.airportsscript
  (:require [babashka.curl :as curl]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [cheshire.core :as json]))


;; Utility functions ;;
(defn csv-data->maps [csv-data]
  (println "Parsing csv into maps...")
  (map zipmap
       (->> (first csv-data) 
            (map keyword) 
            repeat)
    (rest csv-data)))


(defn code-map
  "Nests a map using a kv from the map as the top-level keyword."
  [target-k m]
  (let [k (-> m target-k s/lower-case keyword)]
    {k m}))


;; Airline data ;;
;; Airline colors kindly provided by AirHex.com
;; csv file is *not* version-controlled and won't be shared
;; I changed Etihad's main color to #C4921B (official color)
(def airline-colors
  (->> "../resources/airline-colors-serialized.csv"
       slurp
       csv/read-csv
       (map (fn [[k v]] {(keyword (s/lower-case k)) 
                         {:main-color (re-find #"#[0-9A-Z]{6}" v)}}))
       (into {})))


(def stack-api-key (System/getenv "AVIATIONSTACK_API_KEY"))
(def stack-url "http://api.aviationstack.com/v1/airlines")


(def stack-airlines
  (if (.exists (io/file "../resources/stack_airlines.edn"))
    (edn/read-string (slurp "../resources/stack_airlines.edn"))
    (let [full-url (str stack-url "?access_key=" stack-api-key "&offset=")
          ; need to query the API first to know what's the total n
          first-url (str full-url 0)
          first-res (-> first-url curl/get :body (json/parse-string true))
          first-data (:data first-res)
          offset-seq (as-> first-res $
                           (:pagination $)
                           (:total $)
                           (range 1 $ 100)
                           (drop 1 $))
          url-seq (map #(str full-url %) offset-seq)]
      (reduce (fn [p c]
               (let [res (-> c 
                             curl/get 
                             :body 
                             (json/parse-string true) 
                             :data)] 
                (println c)
                (reduce conj p res)))
              first-data
              url-seq))))


;; Can't use the API more than 500 calls/month
; (->> stack-airlines
;      pr-str
;      (spit "./stack-airlines-dump.edn"))


(defn merge-maps 
  "Merges two nested maps with the same top-level keyword."
  [mapy [k v]]
  {k (conj v (k mapy))})
  
(def merge-colors (partial merge-maps airline-colors))


(def airlines-icao (->> stack-airlines 
                        (filter #(and 
                                   (seq (:icao_code %)) 
                                   (= (:status %) "active")))
                        ;; FIXME: Add Lufthansa
                        (map #(code-map :icao_code %)) 
                        (filter #(contains? airline-colors (first (keys %)))) 
                        (into {})
                        (map merge-colors)
                        (into {})))


(println "Saving airlines db...")
(->> airlines-icao
     pr-str
     (spit "../resources/airlines_icao.edn"))


(println "")
;; Airplanes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def airplane-svg (slurp "./airplane.svg"))
(def root-path "../resources/airplanes/")


(defn spit-airplane [root-path coll] 
  (doseq [[k v] coll]
    (spit (str root-path "svg/" (name k) ".svg") v)))


(->> airlines-icao
     (map (fn [[k v]] 
            [k (s/replace airplane-svg "#5d9cec" (:main-color v))])) 
     (spit-airplane root-path))


(defn ready-png-airplanes [root-path airline] 
  (let [icao (name (first airline))]
    (println "Converting " icao " to PNG")
    (shell/sh "bash" "-c"
              (str "rsvg-convert -h 100 " root-path "svg/" icao ".svg > "
                   root-path "png/base/" icao ".png"))
    (doseq [angle (range 0 360 12)] 
      (println "Rotating " icao " with angle " angle)
      (shell/sh "bash" "-c"
                (str "convert " root-path "png/base/" icao ".png "
                     "-distort ScaleRotateTranslate " angle
                     " +repage " root-path "png/rotations/" icao "_" angle ".png")))))


(println "Converting SVG airplanes to PNG and rotating...")
(pmap #((partial ready-png-airplanes root-path) %) airlines-icao)


(println "")
;; Airport data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn all-airports []
  (println "Getting airport data from ourairports.com...")
  (-> "https://ourairports.com/data/airports.csv"
      curl/get
      :body))


(defn airports-to-keep [airports-map] 
  (println "Keeping airports with scheduled services...")
  (->> airports-map
       ;; only scheduled flights for now so no helipads sorry : (
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
        lat+ (+ latitude latitude-dif)
        lon+ (+ longitude longitude-dif)
        lat- (- latitude latitude-dif)
        lon- (- longitude longitude-dif)
        latmin (min lat+ lat-)
        latmax (max lat+ lat-)
        lonmin (min lon+ lon-)
        lonmax (max lon+ lon-)]
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
     (map #(code-map :iata_code %))
     (into {})
     pr-str
     (spit "../resources/airports_with_boxes.edn"))


(println "All done!")
(println "OK Bye!")


(System/exit 0)
