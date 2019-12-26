(ns controltower.core
  (:require
   [cheshire.core :as json]
   [clojure.core.async :refer [thread]]
   [clojure.string :refer [lower-case upper-case]]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [controltower.db :as db]
   [controltower.landingpage :as landingpage]
   [controltower.utils :as utils]
   [org.httpkit.server :as server]
   [org.httpkit.client :as http]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [taoensso.timbre :as timbre]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as sql-builders])
  (:gen-class))

;; ---- Environmental variables --- ;;
(def maps-api-key (System/getenv "GOOGLE_MAPS_API_KEY"))
(def openweather-api-key (System/getenv "OPENWEATHER_API_KEY"))
(def port (Integer/parseInt (or (System/getenv "PORT") "3000")))
(def mapbox-api-key (System/getenv "MAPBOX_ACCESS_TOKEN"))
(def airplane-img-url (System/getenv "CONTROL_TOWER_TEMP_PLANE_URL"))
(def airplane-angles (range 0 372 12))
(def slack-client-id (System/getenv "CONTROL_TOWER_CLIENT_ID"))
(def slack-client-secret (System/getenv "CONTROL_TOWER_CLIENT_SECRET"))
(def slack-oauth-url-state (System/getenv "CONTROL_TOWER_SLACK_OAUTH_STATE"))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
;; encoding is problematic, so I rolled my own json from the csv file
(def all-airports 
  (with-open [r (java.io.PushbackReader.
                 (clojure.java.io/reader "resources/airports_with_boxes.edn"))]
    (binding [*read-eval* false]
      (read r))))

(def directions-mapping
  {:dep "start"
   :arr "finish"})

(defn command->direction
  "Convert slack commands into flight directions"
  [command]
  (let [direction (command directions-mapping)]
    (if (nil? direction)
      {}
      (keyword direction))))


;; from https://boundingbox.klokantech.com
(def bounding-boxes
  {:txl {:e "52.577701,52.558327,13.32212,13.402922"
         :w "52.561077,52.543694,13.182475,13.249971"}
   :sxf {:e "52.430207,52.357171,13.534024,13.704046"
         :w "52.300794,52.373924,13.2937,13.463721"}})

(defn get-bounding-box
  [boxes airport]
  (get-in boxes [airport :boundingbox]))


(defn get-webhook-vars!
  [slack-team-id]
  (-> (sql/query db/ds
                 [(str "select webhook_channel_id, webhook_url, webhook_channel "
                       "from connected_teams where slack_team_id = '"
                       slack-team-id "'")]
                 {:builder-fn sql-builders/as-unqualified-lower-maps})
      first))

(defn iata->city
  "Converts a IATA code to the city name"
  [iata]
  (get-in all-airports [iata :municipality]))

(defn iata->coords
  "Converts a IATA code to the coordinates of the airport"
  [iata]
  (-> (->> (first all-airports)
           (filter #(= (:iata_code %) iata))
           first)
      (select-keys [:latitude_deg :longitude_deg])))

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
  (-> (str "http://api.openweathermap.org/data/2.5/weather?q="
           (java.net.URLEncoder/encode city)
           "&appid=" openweather-api-key)
      get-api-data!))

(defn get-weather-description
  "Get description for current weather from openweather API response"
  [weather-response]
  (-> weather-response
      :weather
      first
      :description))

(defn create-gmaps-str
  "Creates the url needed for geocoding an address with google maps API"
  [latitude longitude]
  (str "https://maps.googleapis.com/maps/api/geocode/json?latlng="
       latitude "," longitude "&key=" maps-api-key))

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
        address (:formatted_address gmaps-response)]
    (str "Flight " (:flight flight)
         " (" (:aircraft flight) ") "
         (if (and (empty? (:start flight))
                  (empty? (:end flight)))
           "with unknown destination"
           (str "from " (iata->city (keyword (lower-case (:start flight)))) " (" (:start flight) ")"
                " to " (iata->city (keyword (lower-case (:end flight)))) " (" (:end flight) ")"))
         " currently moving at " (:speed flight) " km/h over " address
         " at an altitude of " (:altitude flight) " meters.")))

(defn create-mapbox-str
  "Creates mapbox string for image with map and airplane"
  [image-url longitude latitude night-mode only-airport]
  (str "https://api.mapbox.com/styles/v1/mapbox/"
       (if night-mode "dark-v10" "outdoors-v11")
       "/static/"
       (if (nil? image-url)
         ""
         (str "url-" image-url
              "(" longitude "," latitude ")/"))
       longitude "," latitude
       "," (if only-airport 10 14)
       ",0,0/200x200?attribution=false&logo=false&access_token="
       mapbox-api-key))

(defn create-payload
  "Create a map to be converted into JSON for POST"
  [flight airport]
  (println flight)
  (let [latitude (:lat flight)
        longitude (:lon flight)
        city (iata->city airport)
        weather-response (get-weather! city)]
    (if (empty? flight)
      (let [weather-description (get-weather-description weather-response)]
        {:text (str "Tower observes " weather-description
                    ", no air traffic, over.")})
      (let [night-mode (utils/night? weather-response)
            airline-iata (re-find #"^[A-Z0-9]{2}" (:flight flight))
            plane-angle (utils/closest-int (:track flight) 1 airplane-angles)
            plane-url (str (utils/replace-airline-iata airplane-img-url airline-iata)
                           (apply int plane-angle) ".png")]
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
                                                 night-mode
                                                 false)
                   :alt_text "flight overview"}]}))))

(defn get-flight!
  "Calls flightradar24 cleans the data and extracts the first flight"
  [airport flight-direction]
  (-> (str "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds="
           (get-bounding-box all-airports airport))
      get-api-data!
      remove-crud
      filter-landed
      first-flight
      extract-flight))

(defn flight!
  [airport]
  (let [coordinates (get-bounding-box all-airports airport)]
    (timbre/info "Checking for flights at" airport "at coordinates" coordinates)
    (-> (str "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds="
             coordinates)
        get-api-data!
        remove-crud
        filter-landed
        first-flight
        extract-flight)))

(defn visible-flight
  [airport]
  (let [coordinates (get-bounding-box all-airports airport)
        airport-iata (upper-case (name airport))]
    (->> (pmap #(flight! airport-iata %) coordinates)
         doall
         first)))

(defn post-flight!
  "Gets flight, create string and post it to Slack"
  [airport flight-direction response-url]
  (-> (visible-flight airport flight-direction)
      (create-payload airport)
      (post-to-slack! response-url)))

(defn post-all-airports-flight!
  "Gets flight, create string and post it to Slack"
  [airport response-url]
  (-> (flight! airport)
      (create-payload airport)
      (post-to-slack! response-url)))

(defn request-flight-direction
  [airport user-id]
  (let [airport-iata     (upper-case (name airport))
        airport-coords   (iata->coords airport-iata)
        longitude        (:longitude_deg airport-coords)
        latitude         (:latitude_deg airport-coords)
        city             (iata->city airport-iata)
        weather-response (get-weather! city)
        night-mode       (utils/night? weather-response)]
    {:status 200
     :blocks [{:type "section"
               :text {:type "mrkdwn"
                      :text (str "This is `" airport-iata
                                 "` to user " user-id
                                 " say again!")}}
              {:type "image"
               :title {:type "plain_text"
                       :text airport-iata
                       :emoji true}
               :image_url (create-mapbox-str nil
                                             longitude
                                             latitude
                                             night-mode
                                             true)
               :alt_text "flight overview"}
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
                           :action_id "txl-west"}]}]}))

;; routes and handlers

(defn which-flight
  "Return the current flight"
  [user-id airport flight-direction request]
  (if (contains? bounding-boxes airport)
    (let [team-id (:team_id request)
          webhook-vars (get-webhook-vars! (:team_id request))
          webhook-channel-id (:webhook_channel_id webhook-vars)
          webhook-url (:webhook_url webhook-vars)
          channel-id (:channel_id request)
          response-url (if (= channel-id webhook-channel-id)
                         webhook-url
                         (:response_url request))]
      (thread (post-flight! airport flight-direction response-url))
      (timbre/info "Replying immediately to slack")
      {:status 200
       :body (str "User " user-id " standby...")})

    (do
      ;;NOTE the slash command is already set on slack
      (timbre/error "Flight direction is missing! Asking for more info...")
      (thread (post-to-slack! (request-flight-direction airport user-id)
                              (:response_url request)))
      {:status 200
       :body ""})))

(defn which-flight-allairports
  "Return the current flight"
  [user-id airport request]
  (let [team-id (:team_id request)
        webhook-vars (get-webhook-vars! team-id)
        webhook-channel-id (:webhook_channel_id webhook-vars)
        webhook-url (:webhook_url webhook-vars)
        channel-id (:channel_id request)
        response-url (if (= channel-id webhook-channel-id)
                       webhook-url
                       (:response_url request))]
    (timbre/info "Starting to post flight" )
    (thread (post-all-airports-flight! airport response-url))
    (timbre/info "Replying immediately to slack")
    {:status 200
     :body (str "User " user-id " standby...")}))

(defn insert-slack-token!
  [access-token-response connection]
  (let [incoming-webhook (-> access-token-response
                             :incoming_webhook)
        webhook-url (:url incoming-webhook)
        webhook-channel (:channel incoming-webhook)]
    (sql/insert! connection :connected_teams {:slack_team_id (:team_id access-token-response)
                                              :team_name (:team_name access-token-response)
                                              :registering_user (:user_id access-token-response)
                                              :scope (:scope access-token-response)
                                              :access_token (:access_token access-token-response)
                                              :webhook_url webhook-url
                                              :webhook_channel webhook-channel})
    (timbre/info (str "Done! Team " (:team_name access-token-response)
                      " is connected!"))))

(defn slack-access-token!
  [request]
  (println request)
  (if (= (:state request) slack-oauth-url-state)
    (do
      (timbre/info "Replying to Slack OAuth and saving token to db")
      (-> @(http/post "https://slack.com/api/oauth.access"
                      {:form-params {:client_id slack-client-id
                                     :client_secret slack-client-secret
                                     :code (:code request)
                                     :state slack-oauth-url-state}})
           :body
           (json/parse-string true)
           (insert-slack-token! db/ds)))
    (timbre/error "OAuth state parameter didn't match!")))

(defroutes app-routes
  (GET "/" [] (landingpage/homepage))
  (GET "/slack" req
    (let [request-id (utils/uuid)
          request (:params req)]
      (timbre/info "Received OAuth approval from Slack!")
      (thread (slack-access-token! request))
      (landingpage/homepage)))

  (POST "/which-flight" req
    (let [request-id (utils/uuid)
          request (:params req)
          user-id (:user_id request)
          command (->> (:command request)
                       (re-find #"[a-z]+")
                       keyword)
          flight-direction (command->direction command)
          airport (-> (:text request)
                      lower-case
                      keyword)]
      (timbre/info (str "Slack user " user-id
                        " is requesting info. Checking for flights at "
                        airport "..."))
      (timbre/info (str "request_id:" request-id " saving request in database"))
      (sql/insert! db/ds :requests {:id request-id :user_id user-id
                                    :team_domain (:team_domain request)
                                    :team_id (:team_id request)
                                    :channel_id (:channel_id request)
                                    :channel_name (:channel_name request)
                                    :airport (name airport)
                                    :direction (name flight-direction)
                                    :is_retry 0})
      (which-flight user-id airport flight-direction request)))
  
    (POST "/spot-flight" req
      (let [request-id (utils/uuid)
            request (:params req)
            user-id (:user_id request)
            command (->> (:command request)
                         (re-find #"[a-z]+")
                         keyword)
            airport (-> (:text request)
                        lower-case
                        keyword)]
        (timbre/info (str "Slack user " user-id
                          " is requesting info. Checking for flights at "
                          airport "..."))
        (timbre/info (str "request_id:" request-id " saving request in database"))
        (sql/insert! db/ds :requests {:id request-id :user_id user-id
                                      :team_domain (:team_domain request)
                                      :team_id (:team_id request)
                                      :channel_id (:channel_id request)
                                      :channel_name (:channel_name request)
                                      :airport (name airport)
                                      :direction "NO DIRECTION"
                                      :is_retry 0})
        (which-flight-allairports user-id airport request)))

  (POST "/which-flight-retry" req
    (let [request-id (utils/uuid)
          request (-> req
                      :params
                      :payload
                      (json/parse-string true))
          user-id (:id (:user request))
          received-action (first (:actions request))
          airport (keyword (re-find #"^\w{3}" (:action_id received-action)))
          flight-direction (keyword (:value received-action))
          team-id (:id (:team request))
          webhook-vars (get-webhook-vars! team-id)
          webhook-channel-id (:webhook_channel_id webhook-vars)
          webhook-url (:webhook_url webhook-vars)
          channel-id (:id (:channel request))
          response-url (if (= channel-id webhook-channel-id)
                         webhook-url
                         (:response_url request))]
      (timbre/info (str "Slack user " user-id
                        " is retrying. Checking for flights at "
                        airport "..."))
      (sql/insert! db/ds :requests {:id request-id :user_id user-id
                                    :team_domain (:domain (:team request))
                                    :team_id (:id (:team request))
                                    :channel_id channel-id
                                    :channel_name (:name (:channel request))
                                    :airport (name airport)
                                    :direction (name flight-direction)
                                    :is_retry 1})
      (thread (post-flight! airport flight-direction response-url))
      {:status 200
       :body "Standby..."}))
  (route/resources "/")
  (route/not-found "Error: endpoint not found!"))

(defn -main
  "This is our main entry point"
  []
  (db/migrate)
  (server/run-server (wrap-defaults #'app-routes api-defaults) {:port port})
  (timbre/info
   (str "Control Tower is on the lookout at http:/127.0.0.1:" port "/")))
