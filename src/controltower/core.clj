(ns controltower.core
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.mac :as mac]
   [cheshire.core :as json]
   [clojure.core.async :refer [thread]]
   [clojure.string :refer [trim join split lower-case upper-case]]
   [clojure.walk :refer [keywordize-keys]]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [controltower.db :as db]
   [controltower.landingpage :as landingpage]
   [controltower.utils :as utils]
   [org.httpkit.server :as server]
   [org.httpkit.client :as http]
   [org.httpkit.sni-client :as sni-client]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [taoensso.timbre :as timbre]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as sql-builders])
  (:gen-class))

; Temporary measure until httpkit enables SNI by default
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

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
(def slack-signing-secret (System/getenv "CONTROL_TOWER_SLACK_SIGNING_SECRET"))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
;; see scripts/bounding_boxes.clj for code to reproduce this file
(def all-airports (utils/parse-edn "resources/airports_with_boxes.edn"))

(def airlines-icao (utils/parse-edn "resources/airline_icao_info.edn"))

(defn string->airportkeys
  [variable string]
  (keep (fn [[k v]] (when (= (lower-case (variable v)) 
                             (lower-case string)) k)) all-airports))

(defn string->airportname
  [variable string]
  (let [ks (into [] (string->airportkeys variable string))
        names (into [] (map #(get-in all-airports [% :name]) ks))
        clean-names (map #(clojure.string/replace % #"\s[A|a]irport" "") names)]
    (zipmap ks clean-names)))

(defn slack-element-button
  [[k v]]
  {:type "button"
   :text {:type "plain_text"
          :text v
          :emoji false}
   :value (name k)
   :action_id (str (name k) "-airport")})

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
  (-> (iata all-airports)
      (select-keys [:latitude_deg :longitude_deg])))

(defn iata->name
  [iata]
  (get-in all-airports [iata :name]))

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
  [latitude longitude]
  (timbre/info "Checking the weather...")
  (-> (str "http://api.openweathermap.org/data/2.5/weather?lat=" latitude
           "&lon=" longitude
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

(defn remove-crud
  "Remove irrelevant fields from flightradar24"
  [flight-data]
  (dissoc flight-data :full_count :version))

;; with tips from @seancorfield
(defn f24-with-keywords
  [flight-data]
  (reduce-kv (fn [m k v]
               (assoc m k
                      (zipmap [:id :lat :lon :track :altitude :speed :squawk
                               :radar :aircraft :registration :timestamp :start
                               :end :flight :onground :rateofclimb :icao-flight
                               :isglider :icao-callsign] v))) {} flight-data))

(defn onair-flights
  [flight-data]
  (let [ks (into [] (keep (fn [[k v]]
                            (when (and (= (:onground v) 0)
                                       (> (:altitude v) 150)
                                       (seq (:start v))
                                       (seq (:end v))
                                       (seq (:flight v)))
                                k)) flight-data))]
    (if (empty? ks)
      (do
        (timbre/info "No flights in the air, returning empty map.")
        {})
      (do
        (timbre/info "Returning" (count ks) "flights found in the air")
        (select-keys flight-data ks)))))

(def direction->positions
  {:arr :end
   :dep :start})

(defn filter-direction
  [flight-data airport direction]
  (when (empty? flight-data)
    (timbre/info "No flights to filter, returning empty map.")
    {})
  (timbre/info "Filtering for flights in direction" (name direction))
  (if (= direction :any)
    (do
      (timbre/info "Skipping filtering direction and selecting a random flight")
      flight-data)
    (let [position (direction direction->positions)
          ks (into [] (keep (fn [[k v]]
                              (when (= (lower-case (position v)) (name airport))
                                k)) flight-data))]
      (timbre/info "Returning" (count ks) "flights.")
      (select-keys flight-data ks))))

(defn random-flight
  [flight-data]
  (if (empty? flight-data)
    (do
      (timbre/info "No flights found, returning empty map instead")
      {})
    (let [rand-flight-k (rand-nth (keys flight-data))]
      (timbre/info "Returning random flight" rand-flight-k)
      (rand-flight-k flight-data))))

(defn metric-system-vals
  [flight-data]
  (if (empty? flight-data)
    (do
      (timbre/info "No flights found, returning empty map instead")
      {})
    (do
      (timbre/info "Converting stats to the metric system")
      (assoc-in flight-data [:altitude] (int (* (:altitude flight-data) 3.281)))
      (assoc-in flight-data [:speed] (int (* (:speed flight-data) 1.852))))))

(defn create-flight-str
  "Creates a string with information about the flight"
  [flight airport-iata airline-name]
  (let [gmaps-response (-> (create-gmaps-str (:lat flight) (:lon flight))
                           get-api-data!
                           :results
                           first)
        address (:formatted_address gmaps-response)]
    (str "`" airport-iata "` tower has visual on "
         (if (empty? airline-name)
           ""
           (str airline-name " "))
         "flight " (:flight flight)
         " (" (:aircraft flight) ") "
         (str "from " (iata->city (keyword (lower-case (:start flight)))) " (" (:start flight) ")"
              " to " (iata->city (keyword (lower-case (:end flight)))) " (" (:end flight) ")")
         " currently moving at " (:speed flight) " km/h over " address
         " at an altitude of " (:altitude flight) " meters.")))

(defn create-mapbox-str
  "Creates mapbox string for image with map and airplane"
  [image-url longitude latitude night-mode only-airport]
  (str "https://api.mapbox.com/styles/v1/mapbox/"
       (if night-mode "dark-v10" "streets-v11")
       "/static/"
       (if (nil? image-url)
         ""
         (str "url-" image-url
              "(" longitude "," latitude ")/"))
       longitude "," latitude
       "," (if only-airport 11 14)
       ",0,0/400x300?attribution=false&logo=false&access_token="
       mapbox-api-key))

(defn create-payload
  "Create a map to be converted into JSON for POST"
  [flight airport]
  (let [airport-coords (iata->coords airport)
        airport-lon (:longitude_deg airport-coords)
        airport-lat (:latitude_deg airport-coords)
        weather-response (future (get-weather! airport-lat airport-lon))
        airport-iata (upper-case (name airport))
        airport-name (iata->name airport)
        night-mode (utils/night? @weather-response)]
    (if (empty? flight)
      (let [weather-description (get-weather-description @weather-response)]
        {:blocks [{:type "section"
                   :text {:type "mrkdwn"
                          :text (str "<https://www.openstreetmap.org/#map=14/"
                                     airport-lat "/" airport-lon
                                     " | " airport-name ">"
                                     " tower observes " weather-description
                                     ", no air traffic, over.")}}
                  {:type "image"
                   :title {:type "plain_text"
                           :text (str airport-iata " airport")
                           :emoji true}
                   :image_url (create-mapbox-str nil
                                                 airport-lon
                                                 airport-lat
                                                 night-mode
                                                 true)
                   :alt_text (str airport-iata " airport")}]})
      (let [flight-lon (:lon flight)
            flight-lat (:lat flight)
            airline-iata (re-find #"^[A-Z0-9]{2}" (:flight flight))
            callsign (keyword (:icao-callsign flight))
            airline-name (get-in airlines-icao [callsign :airline])
            plane-angle (utils/closest-int (:track flight) 1 airplane-angles)
            plane-url (str (if (contains? airlines-icao callsign)
                             (utils/replace-airline-iata airplane-img-url airline-iata)
                             (utils/replace-airline-iata airplane-img-url "DEFAULT"))
                           (apply int plane-angle) ".png")]
        (timbre/info (str "Creating payload for " flight))
        {:blocks [{:type "section"
                   :text {:type "mrkdwn"
                          :text (create-flight-str flight airport-iata airline-name)}}
                  {:type "image"
                   :title {:type "plain_text"
                           :text (or (:flight flight) "Flight location")
                           :emoji true}
                   :image_url (create-mapbox-str plane-url
                                                 flight-lon
                                                 flight-lat
                                                 night-mode
                                                 false)
                   :alt_text "flight overview"}]}))))

(defn flight!
  [airport direction]
  (let [coordinates (get-bounding-box all-airports airport)]
    (timbre/info "Checking for flights at" airport "at coordinates" coordinates)
    (-> (str "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds="
             coordinates)
        get-api-data!
        remove-crud
        f24-with-keywords
        onair-flights
        (filter-direction airport direction)
        random-flight
        metric-system-vals)))

(defn post-all-airports-flight!
  "Gets flight, create string and post it to Slack"
  [airport direction response-url]
  (-> (flight! airport direction)
      (create-payload airport)
      (post-to-slack! response-url)))

(defn request-airport-iata
  [city user-id]
  (let [ks (string->airportname :municipality city)]
    {:status 200
     :blocks [{:type "section"
               :text {:type "plain_text"
                      :text (str "This is ATC to user " user-id
                                 " say again! Which airport?")}}
              {:type "actions"
               :elements (into [] (map slack-element-button ks))}]}))

;; routes and handlers
(defn which-flight-allairports
  "Return the current flight"
  [user-id airport direction request]
  (let [team-id (:team_id request)
        webhook-vars (get-webhook-vars! team-id)
        webhook-channel-id (:webhook_channel_id webhook-vars)
        webhook-url (:webhook_url webhook-vars)
        channel-id (:channel_id request)
        response-url (if (= channel-id webhook-channel-id)
                         webhook-url
                         (:response_url request))]
    (timbre/info "Starting to post flight")
    (thread (post-all-airports-flight! airport direction response-url))
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

(defn retry-value
  "Get the value from a retry request"
  [params]
  (timbre/info "Getting airport value from retry request")
  (let [airport-v (-> params
                      :payload
                      (json/parse-string true)
                      :actions
                      first
                      :value)]
    (timbre/info "Retry value is" airport-v)
    airport-v))

(defn determine-direction
 [tokens]
 (let [k (if (< (count tokens) 2)
          :any
          (->> tokens
           last
           (re-find #"^arr|^dep|^any")
           keyword))]
  (if (nil? k)
   :any
   k)))

(defn prepare-req-text
  "Middleware that splits the req-text into [direction] and [airport]."
  [handler]
  (fn
    [request]
    (if (= :post (:request-method request))
      (let [params (:params request)
            raw-req-text (or (:text params) (retry-value params))
            tokens (-> raw-req-text
                       trim
                       lower-case
                       (split #"\s"))
            direction (determine-direction tokens) 
            airport-or-city (if (= direction :any)
                             (join " " tokens)
                             (->> (filter #(not (= % (last tokens))) tokens)
                                  (join " ")))
            airport (if (= "random" airport-or-city)
                      (rand-nth (keys all-airports))
                      (keyword airport-or-city))
            request-type (if (contains? all-airports airport) "airport" "city")  
            request' (assoc request 
                            :direction direction 
                            :airport airport
                            :request-type request-type)]
       (handler request'))
      (handler request))))


(defroutes api-routes
  ;; There are two elements to a call: /spot [airport] [direction]
  ;; [direction] is set to "random" by default
  ;; [airport] always needs input and controltower will request input if it's missing
  ;; [airport] can be either a IATA code, a city or "random"
  ;; /spot random -> is implicitly /spot random random
  ;; ATC response should include the direction too: 
  ;; "Flight 123 is [arriving/departing]" in case the flight is to/from [airport]
  ;; otherwise "Flight 123 from [IATA] to [IATA]"
  ;;
  ;; 1- split incoming string and only look at the first and last
  ;; 1.2 - if first = 'random' and last = nil, then random-flight
  ;; 1.3 - if first in [arr|dep] and last in airports, then flight with direction
  ;; 1.4 - if none of the above then ask for more information 
  ;; 2- if failed, then 

  (POST "/spot-flight" req
    (let [request-id (utils/uuid)
          request (:params req)
          user-id (:user_id request)
          user-name (:user_name request)
          airport (:airport req)
          airport-str (name airport)
          direction (:direction req)
          request-type (:request-type req)]
        (timbre/info (str "Slack user " user-id " (" user-name ")"
                          " is requesting info. Checking for flights at "
                          (upper-case airport-str)) "...")
        (cond 
          (= request-type "airport")
          (do
            (timbre/info (str "request_id:" request-id " saving request in database"))
            (sql/insert! db/ds :requests {:id request-id 
                                          :user_id user-id
                                          :team_domain (:team_domain request)
                                          :team_id (:team_id request)
                                          :channel_id (:channel_id request)
                                          :channel_name (:channel_name request)
                                          :airport airport-str
                                          :direction (name direction)
                                          :is_retry 0})
            (which-flight-allairports user-id airport direction request))

          (= request-type "city")
          (do
            (timbre/info (str "Slack user " user-id
                              " is checking for airports at "
                              airport-str "..."))
            (thread (post-to-slack! (request-airport-iata airport-str user-id) (:response_url request)))
            {:status 200
             :body ""})

          :else (do
                  (timbre/warn airport " is not known!")
                  {:status 200
                   :body (str "User " user-id " please say again. ATC does not know "
                              "`" airport-str "`")}))))

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
          (thread (post-all-airports-flight! airport :any response-url))
          {:status 200
           :body "Standby..."})))

(defroutes page-routes
  (GET "/" [] (landingpage/homepage))
  (GET "/slack" req
       (let [request (:params req)]
         (timbre/info "Received OAuth approval from Slack!")
         (thread (slack-access-token! request))
         (landingpage/homepage))))


(defn from-slack?
  [timestamp payload slack-signature]
  (mac/verify (str "v0:" timestamp ":" payload)
              (codecs/hex->bytes slack-signature)
              {:key slack-signing-secret :alg :hmac+sha256}))

(defn verify-slack-request
  [handler]
  (fn [request]
    (if (= :post (:request-method request))
      (let [headers (keywordize-keys (:headers request))
            slack-signature (-> (:x-slack-signature headers)
                                (clojure.string/replace #"v0=" ""))
            req-timestamp (:x-slack-request-timestamp headers)
            slack-request? (from-slack? req-timestamp (:raw-body request) slack-signature)]
        (if slack-request?
          (do
            (timbre/info "Verified HMAC from Slack.")
            (handler request))
          (do
            (timbre/warn "Received request with incorrect HMAC!")
            {:status 403
             :body (str "403 Forbidden - Incorrect HMAC")})))
      (handler request))))

(defroutes app-routes
  page-routes
  api-routes
  (route/resources "/")
  (route/not-found "Error: endpoint not found!"))

;; --- experimental middleware ---
;; FIXME: verification should be as early as possible, done like this for know
;; because I'm lazy to add different middlewares to different endpoint
(defn keep-raw-json
  "Middleware that compresses responses with gzip for supported user-agents."
  [handler]
  (fn
    [request]
    (if (= :post (:request-method request))
      (let [raw-body (slurp (:body request))
            request' (-> request
                         (assoc :raw-body raw-body)
                         (assoc :body (-> raw-body
                                          (.getBytes "UTF-8")
                                          java.io.ByteArrayInputStream.)))]
        (timbre/info "Keeping raw json intact...")
        (handler request'))
      (handler request))))


(def app
  (-> app-routes
      prepare-req-text
      verify-slack-request
      (wrap-defaults api-defaults)
      keep-raw-json))

(defn -main
  "This is our main entry point"
  []
  (db/migrate)
  (server/run-server app {:port port})
  (timbre/info
   (str "Control Tower is on the lookout at http:/127.0.0.1:" port "/")))
