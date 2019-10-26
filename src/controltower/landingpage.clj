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
          [:div {:class "cf ph3"}
           [:nav {:class "w-90 w-80-ns mt4 center"}
            [:img {:src "/img/drawing.svg" :class "mw4"}]]
           [:div {:class "flex flex-wrap-reverse w-90 w-80-ns mt5 center items-center"}
             [:div {:class "fl w-100 w-third-ns mt5 mt0-ns pr3-m pr5-ns"}
               [:video {:autoplay "" :class "mw-100"}
                 [:source {:src "vid/slack_mockup.mp4" :type "video/mp4"}]]]
             [:div {:class "w-100 w-two-thirds-ns ph2 pr4-ns"}
                [:p {:class "f3 f2-ns mt0 mb0" :style "font-weight:200"}
                 "The best bot"]
                [:p {:class "f2 f1-ns mt0 lh-title"} "to slack off at work"]
                [:p {:class "mb1"} "We were tired of looking out of the window and wondering."]
                [:p {:class "mt0 lh-copy"} "Then did this, and our lives changed forever."]
              [:a {:href "https://slack.com/oauth/authorize?scope=commands&client_id=110684212641.693659198309"}
               [:img {:alt "Add to slack" :height 40 :width 139
                      :src "https://platform.slack-edge.com/img/add_to_slack.png"
                      :srcset "https://platform.slack-edge.com/img/add_to_slack.png 1x, https://platform.slack-edge.com/img/add_to_slack@2x.png 2x"}]]]]
           [:footer {:class "fl w-100 pv4 ph3 ph5-m ph6-l moon-gray mt5"}
            [:small {:class "f6 db tc"} "©"
             [:span {:class "ttu b"} "unlimited budget llc"] " (not a real company)"]
            [:small {:class "tc db mt3"}
             "Made with "
             [:svg {:width 14 :height 10 :viewBox "335 8 14 10"
                    :xmlns "http://www.w3.org/2000/svg"}
              [:path {:d "M342 10.874c.273-.86.69-1.512 1.252-1.956.562-.444 1.243-.667 2.044-.667.947 0 1.71.29 2.285.87.577.578.865 1.343.865 2.296 0 .834-.2 1.506-.6 2.014-.4.508-1.17 1-2.307 1.48l-.147.065c-1.864.786-2.994 1.665-3.39 2.637-.405-.977-1.533-1.856-3.384-2.637a4.018 4.018 0 0 0-.16-.066c-1.144-.48-1.914-.97-2.312-1.473-.398-.503-.597-1.177-.597-2.02 0-.954.29-1.72.868-2.298.58-.58 1.34-.87 2.282-.87.805 0 1.488.224 2.047.668.56.444.978 1.096 1.256 1.956z"
                      :fill "#ccc" :opacity ".5" :fill-rule "evenodd"}]]
             " and mate in Berlin"]]]]))
