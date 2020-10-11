(ns controltower.landingpage
  (:require [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [controltower.edndata :refer [all-airports]])
  (:gen-class))

(def slack-oauth-url-state (System/getenv "CONTROL_TOWER_SLACK_OAUTH_STATE"))

(def n-airports (format "%,12d" (count (keys all-airports))))


(def add-slack-btn
  [:a
   {:class
      "v-mid f6 link pl2 pr3 pv2 mv2 dib black ba br3 b--near-white bg-white",
    :href
      (str
        "https://slack.com/oauth/v2/authorize?scope=incoming-webhook,commands&client_id=817564915318.803887856067&state="
        slack-oauth-url-state)}
   [:img
    {:src "/img/Slack_Mark_Web.svg", :class "pr2 v-mid", :height 20, :width 20}]
   [:span {:class "dark-grey"} "Add to " [:span {:class "b"} "Slack"]]])

(defn plane-screenshots
  ([path viz]
   [:div {:class (str "fl w-100 w-20-ns ph1 tc db-ns " viz)}
    [:div [:img {:src path, :class "mb2"}]]])
  ([path] (plane-screenshots path "")))

(defn plane-svg
  ([path airline-name viz]
   [:div {:class (str "fl w-100 w-20-ns ph1 tc db-ns " viz)}
    [:div [:img {:src path}] [:p {:class "ma0 code f5"} airline-name]]])
  ([path airline-name] (plane-svg path airline-name "")))

(defn svg-icon
  ([url path viewbox]
   [:a {:href url, :class "mh2 moon-gray hover-orange"}
    [:svg
     {:class "dib h2 w2",
      :fill "currentColor",
      :xmlns "http://www.w3.org/2000/svg",
      :viewBox (str "0 0 " viewbox),
      :fill-rule "evenodd",
      :clip-rule "evenodd",
      :stroke-linejoin "round",
      :stroke-miterlimit "1.414"} [:path {:d path, :fill-rule "non-zero"}]]])
  ([url path] (svg-icon url path "16 16")))

(defn homepage
  []
  (html5
    {:lang "en"}
    [:head
     (include-css "https://unpkg.com/tachyons@4.10.0/css/tachyons.min.css")
     [:title "Control Tower"]
     [:meta
      {:charset "utf-8",
       :name "viewport",
       :content "width=device-width, initial-scale=1.0"}]]
    [:body {:style "background-color:#FAFAFA"}
     [:div {:class "center"}
      [:div {:class "pb7 mb5", :style "background-color:#8FAADC"}
       [:div {:class "pt5 pt6-ns center"}
        [:p {:class "tc"}
         [:a {:class "black code f4 b v-mid link", :href ""} [:span "control"]
          [:img {:src "/img/ct_logo.svg", :class "mw1 ph2 v-mid"}]
          [:span "tower"]]]]
       [:div {:class "tc avenir pt4 w-90 center"}
        [:p {:class "mv1 white fw4 f3 f2-ns"} "The best bot"]
        [:p {:class "mt1 mb3 f2-ns f3 fw7"} "to slack off at work"]
        add-slack-btn]]
      [:div {:class "w-90 w-60-ns center mb4"}
       [:div {:class "cf mw7 center mb5"}
        (plane-screenshots "/img/1U2_day.png" "dn")
        (plane-screenshots "/img/2WO_night.png")
        (plane-screenshots "/img/3KL_day.png")
        (plane-screenshots "/img/4LH_day.png")
        (plane-screenshots "/img/5EY_night.png" "dn")]
       [:div {:class "f3-ns f4 lh-copy measure athelas"}
        [:p
         "We saw flights landing in nearby Tegel airport from our office, and wondered where all those airplanes came from. Sure, there is flightradar24.com, but that was too easy. So I made control tower, and now we ask Slack for answers."]
        [:p
         "To work the bot needs permissions for slash commands, and incoming hooks. We have a dedicated channel #planespotting for planespotting where each sighting is posted.
The control tower will reply privately if you call it anywhere else—this way you don't need to change channels."]
        [:img {:class "mv5-ns mv4", :src "/img/screenshot.png"}]
        [:div {:class "cf"}
         [:div {:class "fl w-70-ns w-100 pr3-ns"}
          [:p "The control tower supports " n-airports
           " airports with scheduled flights in the world thanks to data from https://ourairports.com/. To look for flights use the command /spot followed by either the IATA code of the airport, the name of the city in english or random to look at a random airport. If the control tower doesn't see any flights in the air, you get back the current weather in that location.
"]]
         [:div {:class "fl w-30-ns w-100 pl4"}
          [:p {:class "v-mid code"} "/spot TXL" [:br] "/spot Berlin" [:br]
           "/spot random"]]]
        [:div {:class "cf mv5-ns mv4 mw7-ns mw5 center"}
         (plane-svg "/img/TP.svg" "TAP Portugal")
         (plane-svg "/img/ET.svg" "Ethiopian Airlines" "dn")
         (plane-svg "/img/NH.svg" "All Nippon Airways" "dn")
         (plane-svg "/img/EY.svg" "Etihad" "dn")
         (plane-svg "/img/QF.svg" "Qantas" "dn")]
        [:div {:class ""}
         [:p
          "The airplane marker will feature the most common color from the airline’s logo. We support more than 800 airlines thanks for the nice folks at www.airhex.com."]]
        [:div {:class ""}
         [:p [:span {:class "b"} "Privacy policy: "]
          "The control tower does not store any information beyond the name of the team connected, the user id and time of each request. No other sensitive information is stored."]]]]
      [:div {:class "mt5 mb3 pv5 avenir f4", :style "background-color:#8FAADC"}
       [:div {:class "w-60-ns w-90 center lh-copy"}
        [:p {:class ""} "Take a break. Look outside. Spot planes." [:br]
         "Your mind will thank you."] add-slack-btn]]
      [:footer {:class "athelas w-100 pv4 ph3 ph5-m ph6-l light-silver"}
       [:small {:class "tc db mt3"} "Made with "
        [:svg
         {:width 14,
          :height 10,
          :viewBox "335 8 14 10",
          :xmlns "http://www.w3.org/2000/svg"}
         [:path
          {:d
             "M342 10.874c.273-.86.69-1.512 1.252-1.956.562-.444 1.243-.667 2.044-.667.947 0 1.71.29 2.285.87.577.578.865 1.343.865 2.296 0 .834-.2 1.506-.6 2.014-.4.508-1.17 1-2.307 1.48l-.147.065c-1.864.786-2.994 1.665-3.39 2.637-.405-.977-1.533-1.856-3.384-2.637a4.018 4.018 0 0 0-.16-.066c-1.144-.48-1.914-.97-2.312-1.473-.398-.503-.597-1.177-.597-2.02 0-.954.29-1.72.868-2.298.58-.58 1.34-.87 2.282-.87.805 0 1.488.224 2.047.668.56.444.978 1.096 1.256 1.956z",
           :fill "#ccc",
           :opacity ".5",
           :fill-rule "evenodd"}]] " and mate in Berlin."]
       [:div {:class "tc mt3 center"}
        (svg-icon
          "https://github.com/jcpsantiago/controltower"
          "M8 0C3.58 0 0 3.582 0 8c0 3.535 2.292 6.533 5.47 7.59.4.075.547-.172.547-.385 0-.19-.007-.693-.01-1.36-2.226.483-2.695-1.073-2.695-1.073-.364-.924-.89-1.17-.89-1.17-.725-.496.056-.486.056-.486.803.056 1.225.824 1.225.824.714 1.223 1.873.87 2.33.665.072-.517.278-.87.507-1.07-1.777-.2-3.644-.888-3.644-3.953 0-.873.31-1.587.823-2.147-.083-.202-.358-1.015.077-2.117 0 0 .672-.215 2.2.82.638-.178 1.323-.266 2.003-.27.68.004 1.364.092 2.003.27 1.527-1.035 2.198-.82 2.198-.82.437 1.102.163 1.915.08 2.117.513.56.823 1.274.823 2.147 0 3.073-1.87 3.75-3.653 3.947.287.246.543.735.543 1.48 0 1.07-.01 1.933-.01 2.195 0 .215.144.463.55.385C13.71 14.53 16 11.534 16 8c0-4.418-3.582-8-8-8")
        (svg-icon
          "mailto:info@controltowerbot.com?subject=Hello!"
          "M12 .02c-6.627 0-12 5.373-12 12s5.373 12 12 12 12-5.373 12-12-5.373-12-12-12zm6.99 6.98l-6.99 5.666-6.991-5.666h13.981zm.01 10h-14v-8.505l7 5.673 7-5.672v8.504z"
          "24 24")]]]]))


(defn successpage
  []
  (html5
    {:lang "en"}
    [:head
     (include-css "https://unpkg.com/tachyons@4.10.0/css/tachyons.min.css")
     [:title "Control Tower"]
     [:meta
      {:charset "utf-8",
       :name "viewport",
       :content "width=device-width, initial-scale=1.0"}]]
    [:body {:style "background-color:#8FAADC"}
     [:div {:class "center"}
      [:div {:class "pb7 mb5", :style "background-color:#8FAADC"}
       [:div {:class "pt5 pt6-ns center"}
        [:p {:class "tc"}
         [:a {:class "black code f4 b v-mid link", :href "/"} [:span "control"]
          [:img {:src "/img/ct_logo.svg", :class "mw1 ph2 v-mid"}]
          [:span "tower"]]]]
       [:div {:class "tc avenir pt4 w-90 center"}
        [:p {:class "mt1 mb3 f2-ns f3 fw7"}
         "The control tower is ready and waiting for you!"]
        [:p "Just open Slack and type " [:span {:class "code"} "/spot help"]
         " to learn how to get started. Have fun!"]]]]]))

