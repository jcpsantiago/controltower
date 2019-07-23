(ns controltower.core-test
  (:require [clojure.test :refer :all]
            [controltower.core :refer :all]))

(def all-airports
  [{:scheduled_service "no",}
   :latitude_deg 59.969,
   :name "Icy Bay Airport",
   :iso_country "US",
   :type "small_airport",
   :municipality "Icy Bay",
   :iso_region "US-AK",
   :elevation_ft 50,
   :iata_code "ICY",
   :id 8126,
   :ident "19AK",
   :longitude_deg -141.662,
   :wikipedia_link "http://en.wikipedia.org/wiki/Icy_Bay_Airport",
   :gps_code "19AK",
   :local_code "19AK"])

(deftest iata-to-city
  (testing "convert city from IATA code"
    (is (= (iata->city "ICY") "Icy Bay"))))

(deftest api-data
  (testing "get data from an API and retrieve only the body"
    (is (= (get-api-data "http://jsonplaceholder.typicode.com/todos/42")
           {:completed false,
            :title "rerum perferendis error quia ut eveniet",
            :id 42,
            :userId 3}))
    (is (= (get-api-data "http://jsonplaceholder.typicode.com/todos/77")
           {:completed false,
            :title "maiores aut nesciunt delectus exercitationem vel assumenda eligendi at",
            :id 77,
            :userId 4}))))
