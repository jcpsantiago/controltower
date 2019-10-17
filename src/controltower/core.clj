(ns controltower.core
  (:require
   [cheshire.core :as json]
   [clojure.core.async :refer [thread]]
   [clojure.string :refer [upper-case]]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [controltower.utils :as utils]
   [org.httpkit.server :as server]
   [org.httpkit.client :as http]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [taoensso.timbre :as timbre])
  (:gen-class))

(def maps-api-key (System/getenv "GOOGLE_MAPS_API_KEY"))
(def openweather-api-key (System/getenv "OPENWEATHER_API_KEY"))
(def port (Integer/parseInt (or (System/getenv "PORT") "3000")))
(def mapbox-api-key (System/getenv "MAPBOX_ACCESS_TOKEN"))
(def airplane-img-url (System/getenv "CONTROL_TOWER_TEMP_PLANE_URL"))
(def airplane-angles (range 0 372 12))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
;; encoding is problematic, so I rolled my own json from the csv file
(def all-airports (utils/parse-json "resources/airport-codes_json.json"))

;; from https://boundingbox.klokantech.com
(def bounding-boxes
  {:txl {:e "52.577701,52.558327,13.32212,13.402922"
         :w "52.561077,52.543694,13.182475,13.249971"}
   :sxf {:e "52.430207,52.357171,13.534024,13.704046"
         :w "52.300794,52.373924,13.2937,13.463721"}})

(defn get-bounding-box
  [airport flight-direction]
  (-> bounding-boxes
      airport
      flight-direction))

(defn iata->city
  "Converts a IATA code to the city name"
  [iata]
  (->> (first all-airports)
       (filter #(= (:iata_code %) iata))
       first
       :municipality))

(defn post-to-slack!
  "Post message to Slack"
  [payload url]
  (-> @(http/post
        url
        {:body (json/generate-string payload)
         :content-type :json})
      (utils/log-http-status "Slack" "POST")))

(defn get-api-data!
  "GET an API and pull only the body"
  [url]
  (json/parse-string
   (:body @(http/get url))
   true))

(defn get-weather!
  "Get current weather condition for a city"
  [city]
  (timbre/info "Checking the weather...")
  (-> (str "http://api.openweathermap.org/data/2.5/weather?q=" city
           "&appid=" openweather-api-key)
      get-api-data!))

(defn get-weather-description
  "Get description for current weather from openweather API response"
  [weather-response]
  (-> weather-response
      :weather
      first
      :description))

(defn night?
  "Determine if it's night or day based on openweather API response"
  [weather-response]
  (let [sys (:sys weather-response)
        localtime (:dt weather-response)
        sunrise (:sunrise sys)
        sunset (:sunset sys)]
    (or (< localtime sunrise) (> localtime sunset))))

(defn create-gmaps-str
  "Creates the url needed for geocoding an address with google maps API"
  [latitude longitude]
  (str "https://maps.googleapis.com/maps/api/geocode/json?latlng="
       latitude "," longitude "&key=" maps-api-key))

(defn get-gmaps-address
  "Get address from google maps api reverse geocoding response"
  [results]
  (timbre/info "Getting the address with google maps geocoding...")
  (:formatted_address results))

;; extracting info from flightradar24 API and cleaning everying
(defn remove-crud
  "Remove irrelevant fields from flightradar24"
  [flight-data]
  (dissoc flight-data :full_count :version))

(defn filter-landed
  [item]
  (timbre/info "Keeping only flights with altitude above 0")
  (into {} (filter #(> (nth (second %) 4) 0) item)))

(defn get-first-plane
  "Get the keyword for the first plane"
  [clean-flight-data]
  (timbre/info "Keeping only first flight in list")
  (first (keys clean-flight-data)))

(defn first-flight
  "Get data for the first plane in the list"
  [clean-flight-data]
  (if (empty? clean-flight-data)
    (do
      (timbre/info "No flights found, returning empty map instead")
      {})
    ((get-first-plane clean-flight-data) clean-flight-data)))

(defn extract-flight
  "Extract the flight information and create a map with appropriate keywords"
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    (do
      (timbre/info "Extracting flight information into map")
      {:flight (nth clean-flight-data 13)
       :start (nth clean-flight-data 11)
       :end (nth clean-flight-data 12)
       :aircraft (nth clean-flight-data 8)
       :lat (nth clean-flight-data 1)
       :lon (nth clean-flight-data 2)
       :altitude (int (/ (nth clean-flight-data 4) 3.281))
       :speed (int (* (nth clean-flight-data 5) 1.852))
       :track (nth clean-flight-data 3)})))

(defn create-flight-str
  "Creates a string with information about the flight"
  [flight]
  (let [gmaps-response (-> (create-gmaps-str (:lat flight) (:lon flight))
                           get-api-data!
                           :results
                           first)
        address (get-gmaps-address (:formatted_address gmaps-response))]
    (str "Flight " (:flight flight)
         " (" (:aircraft flight) ") "
         (if (and (empty? (:start flight))
                  (empty? (:end flight)))
           "with unknown destination"
           (str "from " (iata->city (:start flight)) " (" (:start flight) ")"
                " to " (iata->city (:end flight)) " (" (:end flight) ")"))
         " currently moving at " (:speed flight) " km/h over " address
         " at an altitude of " (:altitude flight) " meters.")))

(defn create-mapbox-str
  "Creates mapbox string for image with map and airplane"
  [image-url longitude latitude night-mode]
  (str "https://api.mapbox.com/styles/v1/mapbox/"
       (if night-mode "dark-v10" "streets-v11")
       "/static/" "url-" image-url
       "(" longitude "," latitude ")/"
       longitude "," latitude
       ",14,0,0/200x200?attribution=false&logo=false&access_token="
       mapbox-api-key))

(defn create-payload
  "Create a map to be converted into JSON for POST"
  [flight airport]
  (let [latitude (:lat flight)
        longitude (:lon flight)
        city (-> airport
                 name
                 upper-case
                 iata->city)
        weather-response (get-weather! city)]
    (if (empty? flight)
      (let [weather-description (get-weather-description weather-response)]
        {:text (str "Tower observes " weather-description
                    ", no air traffic, over.")})
      (let [night-mode (night? weather-response)
            plane-angle (utils/closest-int (:track flight) 1 airplane-angles)
            plane-url (str airplane-img-url (apply int plane-angle) ".png")]
        (timbre/info (str "Creating payload for " flight))
        {:blocks [{:type "section"
                   :text {:type "plain_text"
                          :text (create-flight-str flight)}}
                  {:type "image"
                   :title {:type "plain_text"
                           :text (or (:flight flight) "Flight location")
                           :emoji true}
                   :image_url (create-mapbox-str plane-url
                                                 longitude
                                                 latitude
                                                 night-mode)
                   :alt_text "flight overview"}]}))))

(defn get-flight!
  "Calls flightradar24 cleans the data and extracts the first flight"
  [airport flight-direction]
  (-> (str "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds="
           (get-bounding-box airport flight-direction))
      get-api-data!
      remove-crud
      filter-landed
      first-flight
      extract-flight))

(defn post-flight!
  "Gets flight, create string and post it to Slack"
  [airport flight-direction response-url]
  (-> (get-flight! airport flight-direction)
      (create-payload airport)
      (post-to-slack! response-url)))

(defn request-flight-direction
  [airport user-id]
  {:status 200
   :blocks [{:type "section"
             :text {:type "mrkdwn"
                    :text (str "This is `" (name airport)
                               "` to user " user-id
                               " say again!")}}
            {:type "actions"
             ;;FIXME should automatically get this from bouncing-boxes
             :elements [{:type "button"
                         :text {:type "plain_text"
                                :text "East"
                                :emoji false}
                         :value "e"
                         :action_id "txl-east"}
                        {:type "button",
                         :text {:type "plain_text"
                                :text "West"
                                :emoji false},
                         :value "w"
                         :action_id "txl-west"}]}]})

;; routes and handlers
(defn simple-body-page
  "Simple page for healthchecks"
  [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Yep, it's one of those empty pages..."})

(defn which-flight
  "Return the current flight"
  [user-id airport command-text response-url]
  (if (and (contains? bounding-boxes airport)
           (seq command-text))
    (let [flight-direction (->> command-text
                                ;;FIXME should be dynamic for more directions
                                (re-matches #"(?i)(^e{1}$)|(^w{1}$)")
                                first
                                keyword)]
      (if (nil? flight-direction)
        (do
          (timbre/error "Invalid flight direction!")
          (thread (post-to-slack! (request-flight-direction airport user-id)
                                  response-url))
          {:status 200
           :body ""})
        (do
          (thread (post-flight! airport flight-direction response-url))
          (timbre/info "Replying immediately to slack")
          {:status 200
           :body (str "User " user-id " standby...")})))
    (do
      ;;NOTE the slash command is already set on slack
      (timbre/error "Flight direction is missing! Asking for more info...")
      (thread (post-to-slack! (request-flight-direction airport user-id)
                              response-url))
      {:status 200
       :body ""})))

(defroutes app-routes
  (GET "/" [req] simple-body-page)
  (POST "/which-flight" req
    (let [request (:params req)
          user-id (:user_id request)
          airport (->> (:command request)
                       (re-find #"[a-z]+")
                       keyword)
          command-text (:text request)
          response-url (:response_url request)]
      (timbre/info (str "Slack user " user-id
                        " is requesting info. Checking for flights at "
                        airport "..."))
      (which-flight user-id airport command-text response-url)))
  (POST "/which-flight-retry" req
    (let [request (-> req
                      :params
                      :payload
                      (json/parse-string true))
          user-id (:id (:user request))
          received-action (first (:actions request))
          airport (keyword (re-find #"^\w{3}" (:action_id received-action)))
          flight-direction (keyword (:value received-action))
          response-url (:response_url request)]
      (timbre/info (str "Slack user " user-id
                        " is retrying. Checking for flights at "
                        airport "..."))
      (thread (post-flight! airport flight-direction response-url))
      {:status 200
       :body "Standby..."}))
  (route/resources "/")
  (route/not-found "Error: endpoint not found!"))

(defn -main
  "This is our main entry point"
  []
  (server/run-server (wrap-defaults #'app-routes api-defaults) {:port port})
  (timbre/info
   (str "Control Tower is on the lookout at http:/127.0.0.1:" port "/")))
