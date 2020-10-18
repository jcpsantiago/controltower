(ns controltower.spec
  (:require [clojure.spec.alpha :as spec]))

;; Openweather API response
(spec/def ::description (spec/and string? #(seq %)))
(spec/def ::dt pos-int?)
(spec/def ::sunrise pos-int?)
(spec/def ::sunset pos-int?)
(spec/def ::openweather
  (spec/keys :req-un [::description ::dt ::sunrise ::sunset]))

(spec/def ::latitude (spec/and number? #(<= % 90) #(>= % -90)))
(spec/def ::longitude (spec/and number? #(<= % 80) #(>= % -180)))
(spec/def ::coords (spec/keys :req-un [::latitude ::longitude]))

(spec/def ::iata (spec/and keyword? #(re-find #"^[a-z]{3}$" (name %))))

