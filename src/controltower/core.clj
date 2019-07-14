(ns controltower.core
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.core.async :refer [thread]])
  (:gen-class))

(def hook-url (System/getenv "CONTROL_TOWER_WEBHOOK_PROD"))

(defn post-to-slack [msg url]
  (http/post url {:body (json/write-str {:text msg})
                  :content-type :json}))

;; Not sure if needed, but it's what flighradar24 sends in the API call
;; need to look at other data sources like OpenskyNetwork
(def options {:user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:66.0) Gecko/20100101 Firefox/66.0"
              :headers {"Host" "data-live.flightradar24.com"
                        "Accept" "application/json, text/javascript, */*; q=0.01"
                        "Accept-Language" "en-US,en;q=0.5"
                        "Referer" "https://www.flightradar24.com/52.56,13.35/13"
                        "Origin" "https://www.flightradar24.com"
                        "DNT" "1"
                        "Connection" "keep-alive"
                        "TE" "Trailers"}})

(defn get-flight-data []
  (json/read-str
   (:body @(http/get "https://data-live.flightradar24.com/zones/fcgi/feed.js?bounds=52.59,52.55,13.33,13.46"
                     options))
   :key-fn keyword))

(defn remove-crud [flight-data] (dissoc flight-data :full_count :version))

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
    "Besides some clouds, not much too see in the sky right now. Ask me again later."
    (str "Flight " (:flight flight)
         " (" (:aircraft flight) ") "
         "from " (:start flight) " to " (:end flight)
         " currently at " (:lat flight)"," (:lon flight)
         " moving at " (:speed flight) " km/h.")))

(defn first-flight
  [clean-flight-data]
  (if (empty? clean-flight-data)
    {}
    ((get-first-plane clean-flight-data) clean-flight-data)))


(defn post-flight
  []
  (-> (get-flight-data)
      remove-crud
      first-flight
      extract-flight
      create-flight-str
      (post-to-slack hook-url)))

; Simple Body Page
(defn simple-body-page [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello World"})

; return the current flight landing
(defn which-flight [req]
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
  (GET "/plane" [] (post-flight))
  (route/not-found "Error: endpoint not found!"))

(defn -main
  "This is our main entry point"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (server/run-server (wrap-defaults #'app-routes api-defaults) {:port port})
    (println (str "Running webserver at http:/127.0.0.1:" port "/"))))
