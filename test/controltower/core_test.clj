(ns controltower.core-test
  (:require [clojure.test :refer :all]
            [controltower.core :as core]
            [mock-clj.core :as mc]))

;; saved calls just for testing
(def flight
  {:full_count 12446
   :version 4
   :217046d0 ["3C4010" 52.564 13.3141 109 0 42 "0000" "T-EDDI80" "" ""
              1564121667 "TXL" "" "S267" 1 0 "S267" 0 ""]})

(deftest getting-bounding-boxes
  (testing "if we get the correct bounding boxes"
    (let [boxes {:txl {:e "a crazy number"
                       :w "an even crazier number"}
                 :sxf {:e "distinct coordinate"
                       :w "at this point, who cares?"}}]
      (is (= (core/get-bounding-box boxes :txl :e) "a crazy number"))
      (is (= (core/get-bounding-box boxes :sxf :w) "at this point, who cares?")))))

(deftest convert-iata-codes
  (testing "if we can get the name of the city where an airport is located"
    (let [airports core/all-airports]
      (is (= (core/iata->city "TXL") "Berlin"))
      (is (= (core/iata->city "LIS") "Lisbon"))
      (is (= (core/iata->city "RIS") "Rishiri")))))

(deftest extract-weather-description
  (testing "if we can extract the textual description of the weather state"
    (let [owmap-response {:weather [{:id 800,
                                     :main "Clear",
                                     :description "clear sky",
                                     :icon "01n"}]}]
      (is (= (core/get-weather-description owmap-response))))))

(deftest api-data
  (testing "get data from an API and retrieve only the body"
    (is (= (core/get-api-data! "http://jsonplaceholder.typicode.com/todos/42")
           {:completed false,
            :title "rerum perferendis error quia ut eveniet",
            :id 42,
            :userId 3}))
    (is (= (core/get-api-data! "http://jsonplaceholder.typicode.com/todos/77")
           {:completed false,
            :title "maiores aut nesciunt delectus exercitationem vel assumenda eligendi at",
            :id 77,
            :userId 4}))))

(deftest test-get-api-data
  (mc/with-mock [core/get-api-data! flight]))
