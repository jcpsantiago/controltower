(ns controltower.db
  (:require [controltower.utils :as utils]
            [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

;; --- Database connection and migrations ----
(def postgresql-host
  (let [heroku-url (System/getenv "DATABASE_URL")]
    (if (nil? heroku-url)
      {:host "0.0.0.0", :user "postgres", :dbtype "postgresql"}
      (utils/create-map-from-uri heroku-url))))

(def db postgresql-host)
(def ds (jdbc/get-datasource db))

(defn migrated?
  [table]
  (-> (sql/query ds
                 [(str "select * from information_schema.tables "
                       "where table_name='"
                       table
                       "'")])
      count
      pos?))

(defn migrate
  []
  (when (not (migrated? "requests"))
    (timbre/info "Creating requests table...")
    (jdbc/execute!
      ds
      ["
      create table requests (
        id varchar(255) primary key,
        user_id varchar(255),
        team_id varchar(255),
        channel_id varchar(255),
        channel_name varchar(255),
        team_domain varchar(255),
        airport varchar(255),
        direction varchar(255),
        is_retry int,
        created_at timestamp default current_timestamp
      )"]))
  (when (not (migrated? "connected_teams"))
    (timbre/info "Creating connected_teams table...")
    (jdbc/execute!
      ds
      ["
        create table connected_teams (
          id serial primary key,
          slack_team_id varchar(255),
          team_name varchar(255),
          registering_user varchar (255),
          scope varchar(255),
          access_token varchar(255),
          webhook_channel varchar(255),
          webhook_url varchar(255),
          webhook_channel_id varchar(255),
          created_at timestamp default current_timestamp
        )"]))
  (timbre/info "Database ready!"))
