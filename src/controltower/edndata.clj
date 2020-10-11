(ns controltower.edndata
  (:require [controltower.utils :as utils])
  (:gen-class))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
;; see scripts/bounding_boxes.clj for code to reproduce this file
(def all-airports (utils/parse-edn "resources/airports_with_boxes.edn"))

(def airlines-icao (utils/parse-edn "resources/airlines_icao.edn"))
