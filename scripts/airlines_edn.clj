#!/usr/bin/env bb

(println "Loading airlines codes json...")
(def airlines
  (json/parsed-seq (io/reader "airline_iata_info.json") true))
(println "done!")

(defn airline-icao-map
  [json-map]
  (let [icao (keyword (:icao json-map))]
    (zipmap [icao] [json-map])))

(println "Generating airline icao info EDN")
(->> (pmap airline-icao-map (first airlines))
     (into {})
     doall
     pr-str
     (spit "../resources/airline_icao_info.edn"))
(println "Done!")
(println "OK bye!")

(System/exit 0)
