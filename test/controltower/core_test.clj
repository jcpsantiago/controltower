(ns controltower.core-test
  (:require [clojure.test :refer :all]
            [controltower.core :refer :all]))

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

(deftest parse-json-files
  (testing "read json files correctly"
    (is (= (parse-json "test/controltower/json-file-testing.json")
           '({:completed false, :title "delectus aut autem", :id 1, :userId 1})))))
