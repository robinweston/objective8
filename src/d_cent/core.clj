(ns d-cent.core
  (:use [org.httpkit.server :only [run-server]])
  (:require [clojure.tools.logging :as log]
            [cemerick.friend :as friend]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [bidi.ring :refer [make-handler ->Resources]]
            [taoensso.tower.ring :refer [wrap-tower]]
            [d-cent.config :as config]
            [d-cent.translation :refer [translation-config]]
            [d-cent.storage :as storage]
            [d-cent.workflows.twitter :refer [twitter-workflow]]
            [d-cent.handlers.api :as api-handlers]
            [d-cent.handlers.front-end :as front-end-handlers]))

;; Custom ring middleware

(defn wrap-api-authorize [handler roles]
  (fn [request]
    (if (friend/authorized? roles friend/*identity*)
      (handler request)
      {:status 401})))

(defn inject-db [handler store]
  (fn [request] (handler (assoc request :d-cent {:store store}))))

(def handlers {:index front-end-handlers/index
               :sign-in front-end-handlers/sign-in
               :sign-out front-end-handlers/sign-out
               :email-capture-get  (friend/wrap-authorize front-end-handlers/email-capture-get #{:signed-in})
               :user-profile-post (friend/wrap-authorize front-end-handlers/user-profile-post #{:signed-in})
               :api-user-profile-post api-handlers/api-user-profile-post
               :objective-create (friend/wrap-authorize front-end-handlers/objective-create #{:signed-in})
               :objective-create-post front-end-handlers/objective-create-post
               :objective-view front-end-handlers/objective-view
               :api-objective-post api-handlers/api-objective-post})

(def routes
  ["/" {""                  :index
        "sign-in"           :sign-in
        "sign-out"          :sign-out
        "email"             {:get :email-capture-get}
        "users"             {:post :user-profile-post}
        "api/v1/users"      {:post :api-user-profile-post}
        "static/"           (->Resources {:prefix "public/"})
        "objectives"        {["/create"] :objective-create
                             :post :objective-create-post
                             ["/" :id] :objective-view }
        "api/v1/objectives"  {:post :api-objective-post
                              ["/" :id] :objective-view}}])

(defn app [app-config]
  (-> (make-handler routes (some-fn handlers #(when (fn? %) %)))
      (friend/authenticate (:authentication app-config))
      (wrap-tower (:translation app-config))
      wrap-keyword-params
      wrap-params
      wrap-json-params
      wrap-json-response
      wrap-session
      (inject-db (:store app-config))))

(defonce server (atom nil))
(defonce in-memory-db (atom {}))

(def app-config
  {:authentication {:allow-anon? true
                    :workflows [twitter-workflow]
                    :login-uri "/sign-in"}
   :translation translation-config
   :store in-memory-db})

(defn start-server []
  (let [port (Integer/parseInt (config/get-var "PORT" "8080"))]
    (log/info (str "Starting d-cent on port " port))
    (reset! server (run-server (app app-config) {:port port}))))

(defn -main []
  (start-server))

(defn stop-server []
  (when-not (nil? @server)
    (@server)
    (reset! server nil)))

(defn restart-server []
  (stop-server)
  (start-server))
