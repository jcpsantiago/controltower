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
           [:div {:class "w-80-ns w-90 mt4 center"}
            [:img {:src "/img/drawing.svg" :class "mw4"}]]
           [:div {:class "cf w-80 mt5 center"}
             [:div {:class "fl w-100 w-third-ns ph3"}
               [:video {:autoplay "" :class "mw5 h-50 right"}
                 [:source {:src "vid/slack_mockup.mp4" :type "video/mp4"}]]]
             [:div {:class "fl w-100 w-two-thirds-ns pa2 ph3"}
                [:p {:class "f2 mt0 mb0" :style "font-weight:200"}
                 "The best bot"
                 [:p {:class "f1 mt0 mb1"} "to slack off at work"]]
                [:p "We were tired of looking out of the window and wondering."]
                [:p "Then did this, and our lives changed forever."]
              [:a {:href "https://slack.com/oauth/authorize?scope=commands&client_id=110684212641.693659198309"}
               [:img {:alt "Add to slack" :height 40 :width 139
                      :src "https://platform.slack-edge.com/img/add_to_slack.png"
                      :srcset "https://platform.slack-edge.com/img/add_to_slack.png 1x, https://platform.slack-edge.com/img/add_to_slack@2x.png 2x"}]]]]]]))
