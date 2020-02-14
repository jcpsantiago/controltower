#!/usr/bin/env bb

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

(println "Loading airport codes json...")
(def all-airports
  (json/parsed-seq (io/reader "airport-codes_onlyscheduled.json") true))
(println "done!")

(defn truncate-number
  [x]
  (format "%.2f" x))

(defn maxmin-lonlat
  [airport-info]
  (let [iata (:iata_code airport-info)
        latitude (edn/read-string (:latitude_deg airport-info))
        longitude (edn/read-string (:longitude_deg airport-info))
        latitude-dif (calc-latitude-dif 5500)
        longitude-dif (calc-longitude-dif latitude 5500)
        lat+ (+ latitude latitude-dif)
        lat- (- latitude latitude-dif)
        lon+ (+ longitude longitude-dif)
        lon- (- longitude longitude-dif)
        latmax (truncate-number (max lat+ lat-))
        latmin (truncate-number (min lat+ lat-))
        lonmax (truncate-number (max lon+ lon-))
        lonmin (truncate-number (min lon+ lon-))]
    {(keyword (str/lower-case iata))
     (conj airport-info [:boundingbox (str latmax "," latmin "," lonmin "," lonmax)])}))

(println "Generating bounding boxes...")
(->> (pmap maxmin-lonlat (first all-airports))
     (into {})
     doall
     pr-str
     (spit "../resources/airports_with_boxes.edn"))
(println "Done!")
(println "OK Bye!")

(System/exit 0)
