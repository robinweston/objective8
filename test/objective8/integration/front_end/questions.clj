(ns objective8.integration.front-end.questions
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [oauth.client :as oauth]
            [objective8.handlers.front-end :as front-end]
            [objective8.http-api :as http-api]
            [objective8.config :as config]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.utils :as utils]
            [objective8.core :as core]))

(def USER_ID 1)
(def OBJECTIVE_ID 234)
(def QUESTION_ID 42)
(def INVALID_ID "not-an-int-id")
(def question-view-get-request (mock/request :get (str utils/host-url "/objectives/" OBJECTIVE_ID "/questions/" QUESTION_ID)))
(defn invalid-question-get-request [objective-id question-id]
  (mock/request :get (str utils/host-url "/objectives/" objective-id "/questions/" question-id)))
(def questions-view-get-request (mock/request :get (str utils/host-url "/objectives/" OBJECTIVE_ID "/questions")))

(def default-app (core/app core/app-config))

(facts "about questions"
       (binding [config/enable-csrf false]
         (fact "authorised user can post and retrieve a question against an objective"
               (against-background
                 (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success}
                 (http-api/create-question {:objective-id OBJECTIVE_ID
                                            :created-by-id USER_ID
                                            :question "The meaning of life?"}) 
                 => {:status ::http-api/success
                     :result {:_id QUESTION_ID
                              :objective-id OBJECTIVE_ID}}
                 (oauth/access-token anything anything anything) => {:user_id USER_ID} 
                 (http-api/create-user anything) => {:status ::http-api/success
                                                     :result {:_id USER_ID}}) 
               (let [user-session (helpers/test-context)
                     params {:question "The meaning of life?"}
                     peridot-response (-> user-session
                                          (helpers/with-sign-in "http://localhost:8080/") 
                                          (p/request (str "http://localhost:8080/objectives/" OBJECTIVE_ID "/questions")
                                                     :request-method :post
                                                     :params params))]
                 peridot-response => (helpers/flash-message-contains "Your question has been added!")
                 peridot-response => (helpers/headers-location (str "/objectives/" OBJECTIVE_ID "#questions")))))

       (fact "Any user can view a question against an objective"
             (against-background
               (http-api/get-question OBJECTIVE_ID QUESTION_ID) => {:status ::http-api/success
                                                                    :result {:question "The meaning of life?"
                                                                             :created-by-id USER_ID
                                                                             :objective-id OBJECTIVE_ID
                                                                             :_id QUESTION_ID}}
               (http-api/retrieve-answers OBJECTIVE_ID QUESTION_ID) => {:status ::http-api/success
                                                                        :result []} 
               (http-api/get-objective OBJECTIVE_ID)=> {:status ::http-api/success 
                                                        :result {:title "some title"}})
             (default-app question-view-get-request) => (contains {:status 200})
             (default-app question-view-get-request) => (contains {:body (contains "The meaning of life?")})) 

       (fact "A user should receive a 404 if a question doesn't exist"
             (against-background
               (http-api/get-question OBJECTIVE_ID QUESTION_ID) => {:status ::http-api/not-found})
             (default-app question-view-get-request) => (contains {:status 404})) 

       (tabular
         (fact "A user should see an error page when they attempt to access a question with non-integer ID's"
               (default-app (invalid-question-get-request ?objective_id ?question_id)) => (contains {:status 404}))
         ?objective_id  ?question_id
         INVALID_ID     QUESTION_ID
         OBJECTIVE_ID   INVALID_ID
         INVALID_ID     INVALID_ID)

       (fact "A user should be redirected to the objective page when they attempt to view the questions page for an objective"
             (let [response (default-app questions-view-get-request)
                   objective-url (utils/path-for :fe/objective :id OBJECTIVE_ID)]
               (:status response) => 302
               (get-in response [:headers "Location"]) => objective-url)))
