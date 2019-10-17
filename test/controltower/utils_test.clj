(ns controltower.utils-test
  (:require [clojure.test :refer :all]
            [controltower.utils :as utils]
            [mock-clj.core :as mc]))

(deftest parse-json-files
  (testing "read json files correctly"
    (is (= (first (utils/parse-json "test/controltower/json-file-testing.json"))
           {:userId 1 :id 1 :title "delectus aut autem" :completed false}))))

(deftest get-closest-int
  (testing "we really get the closest integer"
    (let [some-numbers '(96 108 120 132 144 156 168 180)]
      (is (= (utils/closest-int 110 1 some-numbers) '(108)))
      (is (= (utils/closest-int 2 1 some-numbers) '(96)))
      (is (= (utils/closest-int 123 2 some-numbers) '(120 132)))
      (is (= (utils/closest-int 184 2 some-numbers) '(180 168))))))

(deftest is-it-night?
  (testing "if we can detect whether the sun is still out from unix timestamps"
    (let [not-night {:dt 1570860682
                     :sys {:sunrise 1570857938,
                           :sunset 1570897207}}
          so-night {:dt 1570910682
                    :sys {:sunrise 1570857938,
                          :sunset 1570897207}}]
      (is (false? (utils/night? not-night)))
      (is (true? (utils/night? so-night))))))
