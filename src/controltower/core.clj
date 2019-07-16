(ns controltower.core
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [clojure.core.async :refer [thread]]
            [cheshire.core :as json])
  (:gen-class))

(def hook-url (System/getenv "CONTROL_TOWER_WEBHOOK_PROD"))
(def maps-api-key (System/getenv "GOOGLE_MAPS_API_KEY"))
(def openweather-api-key (System/getenv "OPENWEATHER_API_KEY"))
(def port (Integer/parseInt (or (System/getenv "PORT") "3000")))

(defn get-address
  "Get address from google maps api reverse geocoding"
  [m]
  (:formatted_address (first (get m [:results]))))

(defn post-to-slack [msg url]
  (http/post url {:body (json/generate-string {:text msg})
                  :content-type :json}))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
(def all-airports
  (json/parsed-seq (clojure.java.io/reader "resources/airport-codes_json.json")
                   true))

(defn iata->city
  "Matches a IATA code to the city name"
  [iata]
  (:municipality (first (filter #(= (:iata_code %) iata) (first all-airports)))))

(defn get-api-data
  [url]
  (json/parse-string
   (:body @(http/get url))
   true))

(defn get-weather
  "Get current weather condition for a city"
  [city]
  (:description
   (first (:weather
            (get-api-data
              (str "http://api.openweathermap.org/data/2.5/weather?q=" city
                   "&appid=" openweather-api-key))))))

(defn remove-crud
  [flight-data]
  (dissoc flight-data :full_count :version))

(defn get-first-plane [clean-flight-data]
  (first (keys clean-flight-data)))

(defn extract-flight
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    {:flight (nth clean-flight-data 13)
     :start (nth clean-flight-data 11)
     :end (nth clean-flight-data 12)
     :aircraft (nth clean-flight-data 8)
     :lat (nth clean-flight-data 1)
     :lon (nth clean-flight-data 2)
     :altitude (nth clean-flight-data 4)
     :speed (int (* (nth clean-flight-data 5) 1.852))}))

(defn create-flight-str
  [flight]
  (if (empty? flight)
    (str "Besides some " (get-weather "Berlin")
      ", not much too see in the sky right now. Ask me again later.")

    (str "Flight " (:flight flight)
         " (" (:aircraft flight) ") "
         "from " (iata->city (:start flight)) " (" (:start flight) ")"
         " to " (iata->city (:end flight)) " (" (:end flight) ")"
         " currently over "
         (get-address
          (get-api-data
            (str "https://maps.googleapis.com/maps/api/geocode/json?latlng="
                 (:lat flight)"," (:lon flight) "&key=" maps-api-key)))
         " moving at " (:speed flight) " km/h.")))

(defn first-flight
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    ((get-first-plane clean-flight-data) clean-flight-data)))


(defn post-flight
  ""
  []
  (-> (get-api-data "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds=52.59,52.55,13.33,13.46")
      remove-crud
      first-flight
      extract-flight
      create-flight-str
      (post-to-slack hook-url)))

(defn simple-body-page
  [req]
  "Simple page for healthchecks"
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello World"})

(defn which-flight
  "Return the current flight"
  [req]
  (println req)
  (if (= (:command req) "/plane?")
    (do
      (thread (post-flight))
      {:status 200
       :body (str "Doing some quick maths and looking out the window...")})
    {:status 400
     :body (str "Wrong slash command received:" " " (:command req))}))

(defroutes app-routes
  (GET "/" [] simple-body-page)
  (POST "/which-flight" req
        (let [request (get req :params)]
          (which-flight request)))
  (route/not-found "Error: endpoint not found!"))

(defn -main
  "This is our main entry point"
  [& args]
  (server/run-server (wrap-defaults #'app-routes api-defaults) {:port port})
  (println (str "Running webserver at http:/127.0.0.1:" port "/")))
