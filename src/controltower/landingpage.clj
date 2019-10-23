(ns controltower.landingpage
  (:require
   [hiccup.core :refer :all]
   [hiccup.page :refer :all])
  (:gen-class))

(defn homepage
  "Single page website"
  []
  (html5 {:lang "en"}
         [:head (include-css "https://unpkg.com/tachyons@4.10.0/css/tachyons.min.css"
                             "https://rsms.me/inter/inter.css"
                             "/css/style.css")
          [:title "Control Tower"]
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport"
                  :content "width=device-width, initial-scale=1.0"}]]
         [:body
          [:div {:class "ph3"}
           [:div {:class "w-70 mt3 center"}
            [:img {:src "/img/drawing.svg" :style "width:15%"}]]
          [:div {:class "w-70 mt4 center flex flex-column"}
            [:div {:class "fl w-50 mt3 ph3"}
              [:video {:autoplay "" :class "mw-100"}
                [:source {:src "vid/slack_mockup.mp4" :type "video/mp4"}]]]
            [:div {:class "fl w-50 pa2 ph3"}
              [:p {:class "f1" :style "font-weight:200"}
                "The best slack bot\n"
                [:p {:class "f2"} "to waste your time"]]]]]]))
