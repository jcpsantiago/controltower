(ns controltower.landingpage
  (:require
   [hiccup.core :refer :all]
   [hiccup.page :refer :all])
  (:gen-class))

(def slack-oauth-url-state (System/getenv "CONTROL_TOWER_SLACK_OAUTH_STATE"))

(def add-slack-btn
  [:a {:class "f6 link pl2 pr3 pv2 mv2 dib black ba br3 b--near-white bg-white"
       :href (str "https://slack.com/oauth/authorize?scope=incoming-webhook,commands&client_id=817564915318.803887856067&state="
                  slack-oauth-url-state)}
   [:img {:src "/img/Slack_Mark_Web.svg" :class "pr2 v-mid"
          :height 20 :width 20}]
   [:span {:class "dark-grey"} "Add to "
    [:span {:class "b"} "Slack"]]])

(defn homepage
  "Single page website"
  []
  (html5 {:lang "en"}
         [:head (include-css "https://unpkg.com/tachyons@4.10.0/css/tachyons.min.css"
                             "https://rsms.me/inter/inter.css"
                             "/css/style.css"
                             "/css/animate.css"
                             "/css/animated_clouds.css")
          [:title "Control Tower"]
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport"
                  :content "width=device-width, initial-scale=1.0"}]]
         [:body
          [:div {:class "ph0"}
           [:div {:class "min-vh-100"}
            [:div {:class "min-vh-100 w-90 w-80-ns center pt5"}
             [:img {:src "/img/ct_logo.svg" :class "mw1 pr2"}]
             [:a {:class "f5 black code b link"
                  :href ""} "ControlTower"]
             [:div {:class "flex-auto"}]
             ;;[:div
             ;; [:nav {:class "tc lh-title flex"}
              ;; [:a {:class "f5 black code tr no-underline underline-hover"
                ;;    :href "#"} "About"]]

             [:div {:class "w-100 w-two-thirds-ns ph2 pr4-ns center pt6"}
              [:h2 {:class "f3 f2-ns mt0 mb0" :style "font-weight:200"}
               "The best bot"]
              [:h1 {:class "f2 f1-ns mt0 lh-title"} "to slack off at work"]
              [:p {:class "mb1"} "There are countless apps out there to increase your productivity."]
              [:p {:class ""} "Here's one to make you smile."]
              add-slack-btn]]


            ;; https://codepen.io/Mark_Bowley/pen/xEbuI
            [:div {:class "min-vh-100 bg-blue" :id "background-wrap"}
             [:div {:class "x1"}
              [:div {:class "cloud"}]]
             [:div {:class "x2"}
              [:div {:class "cloud"}]]
             [:div {:class "x3"}
              [:div {:class "cloud"}]]
             [:div {:class "x4"}
              [:div {:class "cloud"}]]
             [:div {:class "x5 db-ns dn"}
              [:div {:class "cloud"}]]]]

           [:div {:class "cf ph3 ph5-ns pb5 bg-green black-70"}
            [:h1 {:class "fl w-100 mt5 f5 ttu tracked fw6"} "spotting"]
            [:div {:class "pv2 fl w-100 w-50-l pr0 pr2-l"}
             [:h2 {:class "f4 f2-ns fw6 mb2"} "What's happening at /txl?"]
             ;; https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/url-https%3A%2F%2Fc764e6eb.ngrok.io%2FEW.png(13.214952,52.553030)/13.214952,52.553030,14,0,0/400x400@2x?attribution=false&logo=false&access_token=pk.eyJ1IjoiamNwc2FudGlhZ28iLCJhIjoiY2sxdHAwcm83MHFhNTNtdDYwNjY2dGthYSJ9.wYJZDRIGi8Shn1y5y57Svw
             [:p {:class "f5 measure lh-copy mt0"}
              "Flight EW2000 (A319) from Stuttgart (STR) to Berlin (TXL), currently moving at 325 km/h over Hakenfelde, Berlin, Germany at an altitude of 693 meters."]]
            [:div {:class "pv2 fl w-100 w-50-l pr0 pr2-l"}
             [:img {:src "screenshots/flight_screenshot.png"
                    :class "mw-100 br2"}]]]

           [:div {:class "ph3 ph5-ns pb5 bg-light-blue black-70"}
            [:div {:class "pv2 fl w-100 pr0 pr2-l"}
             [:h2 {:class "f4 f2-ns fw6 mb2"} "Skies full of colors"]
             [:p {:class "f5 measure lh-copy mt0"} "Our state-of-the-art technology produces airplane liveries matching each airline's most common color. Over 200 airlines available!"]]
            [:div {:class "pv2 w-100 pr0 pr2-l tc"}
             [:div {:class "ph3 tc dib"}
              [:img {:src "/img/TP.svg"
                     :class "mw5 flex-ns mb2 dn"}]
              [:span {:class "code dn db-ns"} "TAP Portugal"]]
             [:div {:class "ph3 tc dib"}
              [:img {:src "/img/NH.svg"
                     :class "mw5 flex-ns mb2 dn"}]
              [:span {:class "code dn db-ns"} "All Nippon Airways"]]
             [:div {:class "ph3 tc dib"}
              [:img {:src "/img/EY.svg"
                     :class "mw5 flex-ns mb2"}]
              [:span {:class "code"} "Etihad"]]]]

           [:footer {:class "w-100 pv4 ph3 ph5-m ph6-l moon-gray mt5 v-btm"}
            [:small {:class "tc db mt3"}
             "Made with "
             [:svg {:width 14 :height 10 :viewBox "335 8 14 10"
                    :xmlns "http://www.w3.org/2000/svg"}
              [:path {:d "M342 10.874c.273-.86.69-1.512 1.252-1.956.562-.444 1.243-.667 2.044-.667.947 0 1.71.29 2.285.87.577.578.865 1.343.865 2.296 0 .834-.2 1.506-.6 2.014-.4.508-1.17 1-2.307 1.48l-.147.065c-1.864.786-2.994 1.665-3.39 2.637-.405-.977-1.533-1.856-3.384-2.637a4.018 4.018 0 0 0-.16-.066c-1.144-.48-1.914-.97-2.312-1.473-.398-.503-.597-1.177-.597-2.02 0-.954.29-1.72.868-2.298.58-.58 1.34-.87 2.282-.87.805 0 1.488.224 2.047.668.56.444.978 1.096 1.256 1.956z"
                      :fill "#ccc" :opacity ".5" :fill-rule "evenodd"}]]
             " and mate in Berlin. "
             [:a {:href "mailto:info@controltowerbot.com?subject=Hello!"
                  :class "link moon-gray hover-orange"} "Talk to me!"]]
            [:div {:class "tc mt3 center"}
             [:a {:href "https://github.com/jcpsantiago/controltower"
                  :class "pr2 moon-gray hover-gray"}
              [:svg {:class "dib h2 w2"
                     :fill "currentColor"
                     :xmlns "http://www.w3.org/2000/svg"
                     :viewBox "0 0 16 16"
                     :fill-rule "evenodd"
                     :clip-rule "evenodd"
                     :stroke-linejoin "round"
                     :stroke-miterlimit "1.414"}
               [:path {:d "M8 0C3.58 0 0 3.582 0 8c0 3.535 2.292 6.533 5.47 7.59.4.075.547-.172.547-.385 0-.19-.007-.693-.01-1.36-2.226.483-2.695-1.073-2.695-1.073-.364-.924-.89-1.17-.89-1.17-.725-.496.056-.486.056-.486.803.056 1.225.824 1.225.824.714 1.223 1.873.87 2.33.665.072-.517.278-.87.507-1.07-1.777-.2-3.644-.888-3.644-3.953 0-.873.31-1.587.823-2.147-.083-.202-.358-1.015.077-2.117 0 0 .672-.215 2.2.82.638-.178 1.323-.266 2.003-.27.68.004 1.364.092 2.003.27 1.527-1.035 2.198-.82 2.198-.82.437 1.102.163 1.915.08 2.117.513.56.823 1.274.823 2.147 0 3.073-1.87 3.75-3.653 3.947.287.246.543.735.543 1.48 0 1.07-.01 1.933-.01 2.195 0 .215.144.463.55.385C13.71 14.53 16 11.534 16 8c0-4.418-3.582-8-8-8"}]]]
             [:a {:href "http://www.linkedin.com/in/jcpsantiago"
                  :class "moon-gray hover-gray"}
              [:svg {:class "dib h2 w2"
                     :fill "currentColor"
                     :xmlns "http://www.w3.org/2000/svg"
                     :viewBox "0 0 16 16"
                     :fill-rule "evenodd"
                     :clip-rule "evenodd"
                     :stroke-linejoin "round"
                     :stroke-miterlimit "1.414"}
               [:path {:d "M13.632 13.635h-2.37V9.922c0-.886-.018-2.025-1.234-2.025-1.235 0-1.424.964-1.424 1.96v3.778h-2.37V6H8.51V7.04h.03c.318-.6 1.092-1.233 2.247-1.233 2.4 0 2.845 1.58 2.845 3.637v4.188zM3.558 4.955c-.762 0-1.376-.617-1.376-1.377 0-.758.614-1.375 1.376-1.375.76 0 1.376.617 1.376 1.375 0 .76-.617 1.377-1.376 1.377zm1.188 8.68H2.37V6h2.376v7.635zM14.816 0H1.18C.528 0 0 .516 0 1.153v13.694C0 15.484.528 16 1.18 16h13.635c.652 0 1.185-.516 1.185-1.153V1.153C16 .516 15.467 0 14.815 0z"
                       :fill-rule "non-zero"}]]]]]]]))
