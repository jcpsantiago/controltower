(ns controltower.core-test
  (:require [clojure.test :refer :all]
            [controltower.core :refer :all]
            [mock-clj.core :as mc]))

(deftest api-data
  (testing "get data from an API and retrieve only the body"
    (is (= (get-api-data! "http://jsonplaceholder.typicode.com/todos/42")
           {:completed false,
            :title "rerum perferendis error quia ut eveniet",
            :id 42,
            :userId 3}))
    (is (= (get-api-data! "http://jsonplaceholder.typicode.com/todos/77")
           {:completed false,
            :title "maiores aut nesciunt delectus exercitationem vel assumenda eligendi at",
            :id 77,
            :userId 4}))))

(deftest parse-json-files
  (testing "read json files correctly"
    (is (= (parse-json "test/controltower/json-file-testing.json")
           ({:completed false, :title "delectus aut autem", :id 1, :userId 1})))))

;; saved call just for testing
(def flight
  {:full_count 12446
   :version 4
   :217046d0 ["3C4010" 52.564 13.3141 109 0 42 "0000" "T-EDDI80" "" ""
              1564121667 "TXL" "" "S267" 1 0 "S267" 0 ""]})

(deftest test-get-api-data
  (mc/with-mock [get-api-data! flight]))
