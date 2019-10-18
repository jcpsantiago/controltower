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
          [:div {:class "w-100 fixed"}
           [:div {:class "w-90 center"}
            [:h1 {:class "mb0 f3 f3-ns f3-l ttu tracked-mega"}
             [:a {:class "link white" :href "/"} "Control Tower"]]]]]))
