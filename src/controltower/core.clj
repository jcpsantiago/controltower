(ns controltower.core
  (:require
   [org.httpkit.server :as server]
   [org.httpkit.client :as http]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [clojure.core.async :refer [thread]]
   [cheshire.core :as json]
   [taoensso.timbre :as timbre]
   [clojure2d.core :as c2d]
   [clojure.java.io :as io]
   [cognitect.aws.client.api :as aws]
   [cognitect.aws.credentials :as creds])
  (:import
   [java.awt Graphics2D]
   [java.awt.image BufferedImage]
   [javax.imageio ImageIO])
  (:gen-class))

(def maps-api-key (System/getenv "GOOGLE_MAPS_API_KEY"))
(def openweather-api-key (System/getenv "OPENWEATHER_API_KEY"))
(def port (Integer/parseInt (or (System/getenv "PORT") "3000")))
(def mapbox-api-key (System/getenv "MAPBOX_ACCESS_TOKEN"))
(def airplane-img-url (System/getenv "CONTROL_TOWER_TEMP_PLANE_URL"))
(def s3-bucket (System/getenv "CONTROL_TOWER_S3_BUCKET"))

(defn log-http-status
  [{:keys [status body]} service type]
  (if (not (= status 200))
    (timbre/error "Failed, exception is" body)
    (timbre/info (str service " async HTTP " type " success: ") status)))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn parse-json
  [file]
  (json/parsed-seq (clojure.java.io/reader file)
                   true))

;; list of airports from https://datahub.io/core/airport-codes#data
;; under Public Domain Dedication and License
;; encoding is problematic, so I rolled my own json from the csv file
(def all-airports (parse-json "resources/airport-codes_json.json"))

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

(def orig-airplane-image (-> "resources/public/airplane_small.png"
                             (io/file)
                             (ImageIO/read)))

;; thanks to user Yuhan Quek in Clojurians
;; https://clojurians.slack.com/archives/C03S1KBA2/p1570958786346700
(defn rotate-around-center
  [img degrees]
  (let [w   (.getWidth img)
        h   (.getHeight img)]
    (c2d/with-canvas-> (c2d/canvas w h)
      (c2d/translate (/ w 2) (/ h 2))
      (c2d/rotate (* degrees (/ Math/PI 180)))
      (c2d/translate (- (/ w 2)) (- (/ h 2)))
      (c2d/image img)
      (c2d/get-image))))

(def s3 (aws/client {:api :s3
                     :region :us-east-1
                     :credentials-provider (creds/environment-credentials-provider)}))

(defn add-uuid
  [string uuid extension]
  (str string uuid extension))

(defn image->bytes!
  [image angle]
  (let [output-bytes (java.io.ByteArrayOutputStream.)]
    (-> image
        (rotate-around-center angle)
        (ImageIO/write "png" output-bytes))
    (.toByteArray output-bytes)))

;; the only thing missing for proper public access was ContentType, otherwise
;; the file is saved as a stream or whatever
(defn send-image-s3!
  [image file-path]
  (timbre/info "Uploading to" s3-bucket "S3 bucket...")
  (aws/invoke s3 {:op :PutObject
                  :request {:Bucket s3-bucket :Key file-path
                            :Body image
                            :ACL "public-read"
                            :ContentType "image/png"}}))


(defn iata->city
  "Matches a IATA code to the city name"
  [iata]
  (:municipality (first (filter #(= (:iata_code %) iata) (first all-airports)))))

(defn post-to-slack!
  "Post message to Slack"
  [payload url]
  (-> @(http/post
        url
        {:body (json/generate-string payload)
         :content-type :json})
      (log-http-status "Slack" "POST")))

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
  (:description
   (first (:weather
            (get-api-data!
              (str "http://api.openweathermap.org/data/2.5/weather?q=" city
                   "&appid=" openweather-api-key))))))

(defn get-address
  "Get address from google maps api reverse geocoding"
  [m]
  (timbre/info "Getting the address with google maps geocoding...")
  (:formatted_address (first (get m :results))))


;; extracting info from flightradar24 API and cleaning everying
(defn remove-crud
  "Remove irrelevant fields from flightradar24"
  [flight-data]
  (dissoc flight-data :full_count :version))

(defn get-first-plane
  "Get the keyword for the first plane"
  [clean-flight-data]
  (first (keys clean-flight-data)))

(defn first-flight
  "Get data for the first plane in the list"
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    ((get-first-plane clean-flight-data) clean-flight-data)))

(defn extract-flight
  "Extract the flight information and create a map with appropriate keywords"
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    {:flight (nth clean-flight-data 13)
     :start (nth clean-flight-data 11)
     :end (nth clean-flight-data 12)
     :aircraft (nth clean-flight-data 8)
     :lat (nth clean-flight-data 1)
     :lon (nth clean-flight-data 2)
     :altitude (int (/ (nth clean-flight-data 4) 3.281))
     :speed (int (* (nth clean-flight-data 5) 1.852))
     :track (nth clean-flight-data 3)}))

(defn create-flight-str
  "Creates a string with information about the flight"
  [flight]
  (str "Flight " (:flight flight)
         " (" (:aircraft flight) ") "
         "from " (iata->city (:start flight)) " (" (:start flight) ")"
         " to " (iata->city (:end flight)) " (" (:end flight) ")"
         " currently moving at " (:speed flight) " km/h over "
         (re-find #"[^,]*"
                  (get-address
                    (get-api-data!
                      (str "https://maps.googleapis.com/maps/api/geocode/json?latlng="
                           (:lat flight) "," (:lon flight) "&key=" maps-api-key))))
       " at an altitude of " (:altitude flight) " meters."))

(defn create-mapbox-str
  "Creates mapbox string for image with map and airplane"
  [image-url longitude latitude api-key]
  (str "https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/"
       "url-" image-url
       "(" longitude "," latitude ")/"
       longitude "," latitude
       ",14,0,0/200x200?attribution=false&logo=false&access_token="
       api-key))

(defn create-payload
  "Create a map to be converted into JSON for POST"
  [flight]
  (if (empty? flight)
    {:text (str "Tower observes " (get-weather! "Berlin") ;;FIXME generalize
                ", no air traffic, over.")}
    (let [plane-uuid (uuid)
          plane-url (add-uuid airplane-img-url plane-uuid ".png")
          plane-path (add-uuid "airplanes/airplane_small_temp_" plane-uuid ".png")]
      (timbre/info "Rotating image and uploading to S3 with uuid" plane-uuid)
      ;;FIXME should this S3 upload really be here?
      (-> (image->bytes! orig-airplane-image (:track flight))
          (io/input-stream)
          (send-image-s3! plane-path))
      (timbre/info (str "Creating payload for " flight))
      {:blocks [{:type "section"
                 :text {:type "plain_text"
                        :text (create-flight-str flight)}}
                {:type "image"
                 :title {:type "plain_text"
                         :text (or (:flight flight) "Flight location")
                         :emoji true}
                 :image_url (create-mapbox-str plane-url
                                               (:lon flight)
                                               (:lat flight)
                                               mapbox-api-key)
                 :alt_text "flight overview"}]})))



(defn get-flight!
  "Calls flightradar24 cleans the data and extracts the first flight"
  [airport flight-direction]
  (-> (str "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds="
           (get-bounding-box airport flight-direction))
      get-api-data!
      remove-crud
      first-flight
      extract-flight))

(defn post-flight!
  "Gets flight, create string and post it to Slack"
  [airport flight-direction response-url]
  (-> (get-flight! airport flight-direction)
      create-payload
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
  []
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello World"})

(defn which-flight
  "Return the current flight"
  [user-id airport command-text response-url]
  (if (and (contains? bounding-boxes airport)
           (seq command-text))
    (let [flight-direction (keyword
                            (first
                             (re-matches #"(?i)(^e{1}$)|(^w{1}$)"
                                         command-text)))]
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
  (GET "/" [] simple-body-page)
  (POST "/which-flight" req
        (let [request (:params req)
              user-id (:user_id request)
              airport (keyword (re-find #"[a-z]+" (:command request)))
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
           :body ""}))
  (route/resources "/")
  (route/not-found "Error: endpoint not found!"))

(defn -main
  "This is our main entry point"
  []
  (server/run-server (wrap-defaults #'app-routes api-defaults) {:port port})
  (timbre/info (str "Control Tower is on the lookout at http:/127.0.0.1:" port "/")))
