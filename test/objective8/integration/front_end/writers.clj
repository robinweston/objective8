(ns objective8.integration.front-end.writers
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [oauth.client :as oauth]
            [objective8.config :as config]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.utils :as utils]
            [objective8.core :as core]
            [objective8.handlers.front-end :as front-end]
            [objective8.http-api :as http-api]))

(def TWITTER_ID "twitter-ID")
(def USER_ID 1)
(def OBJECTIVE_ID 2)
(def WRITER_ROLE_FOR_OBJECTIVE (keyword (str "writer-for-" OBJECTIVE_ID)))
(def OBJECTIVE_TITLE "some title")
(def OBJECTIVE_URL (utils/local-path-for :fe/objective :id OBJECTIVE_ID)) 
(def INVITATION_ID 3)
(def UUID "random-uuid")
(def WRITER_EMAIL "writer@email.com")
(def candidates-get-request (mock/request :get (utils/path-for :fe/candidate-list :id OBJECTIVE_ID)))

(def INVITATION_URL (utils/path-for :fe/writer-invitation :uuid UUID))
(def ACCEPT_INVITATION_URL (utils/path-for :fe/accept-invitation :id OBJECTIVE_ID :i-id INVITATION_ID))
(def DECLINE_INVITATION_URL (utils/path-for :fe/decline-invitation :id OBJECTIVE_ID :i-id INVITATION_ID))

(def ACTIVE_INVITATION {:_id INVITATION_ID
                        :invited-by-id USER_ID
                        :objective-id OBJECTIVE_ID
                        :uuid UUID
                        :status "active"})

(def EXPIRED_INVITATION (assoc ACTIVE_INVITATION :status "expired"))

(def default-app (core/app core/app-config))
(def user-session (helpers/test-context))

(facts "about writers"
       (binding [config/enable-csrf false]
         (fact "authorised user can invite a policy writer on an objective"
               (against-background
                 (oauth/access-token anything anything anything) => {:user_id TWITTER_ID}
                 (http-api/create-user anything) => {:status ::http-api/success
                                                     :result {:_id USER_ID}}
                 (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success}
                 (http-api/create-invitation 
                   {:writer-name "bob"
                    :writer-email WRITER_EMAIL
                    :reason "he's awesome"
                    :objective-id OBJECTIVE_ID
                    :invited-by-id USER_ID}) => {:status ::http-api/success
                                                 :result {:_id INVITATION_ID
                                                          :objective-id OBJECTIVE_ID
                                                          :uuid UUID
                                                          :writer-email WRITER_EMAIL}})
               (let [params {:writer-name "bob"
                             :writer-email WRITER_EMAIL
                             :reason "he's awesome"}
                     peridot-response (-> user-session
                                          (helpers/with-sign-in "http://localhost:8080/")
                                          (p/request (str "http://localhost:8080/objectives/" OBJECTIVE_ID "/writer-invitations")
                                                     :request-method :post
                                                     :params params))]
                 (:flash (:response peridot-response)) => 
                 {:type :invitation
                  :writer-email WRITER_EMAIL 
                  :invitation-url INVITATION_URL}
                 peridot-response => (helpers/headers-location (str "/objectives/" OBJECTIVE_ID))))

         (fact "A user should be redirected to objective page when attempting to view the candidate writers page for an objective"
               (let [response (default-app candidates-get-request)
                     objective-url (utils/path-for :fe/objective :id OBJECTIVE_ID)] 
                 (:status response) => 302 
                 (get-in response [:headers "Location"]) => objective-url))))

(facts "about responding to invitations"

       (fact "an invited writer is redirected to the objective page when accessing their invitation link"
             (against-background
               (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                               :result {:_id INVITATION_ID
                                                                        :invited-by-id USER_ID
                                                                        :objective-id OBJECTIVE_ID
                                                                        :uuid UUID
                                                                        :status "active"}}
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                         :result {:title OBJECTIVE_TITLE
                                                                  :uri :objective-uri}}
               (http-api/retrieve-candidates OBJECTIVE_ID) => {:status ::http-api/success :result []} 
               (http-api/retrieve-questions OBJECTIVE_ID) => {:status ::http-api/success :result []} 
               (http-api/get-comments anything) => {:status ::http-api/success
                                                    :result []}) 
             (let [peridot-response (-> user-session
                                        (p/request INVITATION_URL)
                                        p/follow-redirect)]
               peridot-response => (contains {:request (contains {:uri (contains OBJECTIVE_URL)})})
               peridot-response => (contains {:response (contains {:body (contains OBJECTIVE_TITLE)})})))

       (fact "an invited writer is shown a flash banner message with a link to the objective when navigating away from the objective (e.g. learn-more page)"
             (against-background
               (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                               :result {:_id INVITATION_ID
                                                                        :invited-by-id USER_ID
                                                                        :objective-id OBJECTIVE_ID
                                                                        :uuid UUID
                                                                        :status "active"}}
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                         :result {:title OBJECTIVE_TITLE
                                                                  :uri :objective-uri}}
               (http-api/retrieve-candidates OBJECTIVE_ID) => {:status ::http-api/success :result []} 
               (http-api/retrieve-questions OBJECTIVE_ID) => {:status ::http-api/success :result []} 
               (http-api/get-comments anything) => {:status ::http-api/success
                                                    :result []}) 
             (let [peridot-response (-> user-session
                                        (p/request INVITATION_URL)
                                        p/follow-redirect
                                        (p/request (utils/path-for :fe/learn-more)))]
               peridot-response => (contains {:response (contains {:body (contains (str "href=\"" OBJECTIVE_URL))})})))

       (fact "a user is redirected to the objective details page with a flash message if the invitation has expired"
             (against-background
               (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                               :result EXPIRED_INVITATION}
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                         :result {:title OBJECTIVE_TITLE
                                                                  :uri :objective-uri}}
               (http-api/retrieve-candidates OBJECTIVE_ID) => {:status ::http-api/success :result []}
               (http-api/retrieve-questions OBJECTIVE_ID) => {:status ::http-api/success :result []}
               (http-api/get-comments anything) => {:status ::http-api/success
                                                    :result []})
             (let [{request :request response :response} (-> user-session
                                                             (p/request INVITATION_URL)
                                                             p/follow-redirect)]
               (:uri request) => OBJECTIVE_URL
               (:body response) => (contains "This invitation has expired")))

       (fact "an invitation url returns a 404 if the invitation doesn't exist"
             (against-background
               (http-api/retrieve-invitation-by-uuid anything) => {:status ::http-api/not-found})
             (p/request user-session "/invitations/nonexistent-invitation-uuid") => (contains {:response (contains {:status 404})}))

       (fact "a user's invitation credentials are removed from the session when accessing the objective page with invitation credentials that don't match an active invitation" 
             (-> user-session
                 (p/request INVITATION_URL)
                 p/follow-redirect)
             => anything
             (provided 
               (http-api/retrieve-invitation-by-uuid anything) 
               =streams=> [{:status ::http-api/success
                            :result {:_id INVITATION_ID
                                     :invited-by-id USER_ID
                                     :objective-id OBJECTIVE_ID
                                     :uuid :NOT_AN_ACTIVE_UUID
                                     :status "active"}} 
                           {:status ::http-api/not-found}]
               (front-end/remove-invitation-from-session anything) => {})))


(binding [config/enable-csrf false]
  (facts "accepting an invitation"
         (against-background
           (oauth/access-token anything anything anything) => {:user_id TWITTER_ID}
           (http-api/create-user anything) => {:status ::http-api/success
                                               :result {:_id USER_ID}})
         (fact "a user can accept an invitation when they have invitation credentials and they're signed in"
               (against-background
                 (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                                 :result ACTIVE_INVITATION}
                 (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                           :result {:title OBJECTIVE_TITLE}})
               (let [{request :request} (-> user-session
                                            (helpers/with-sign-in "http://localhost:8080/")
                                            (p/request INVITATION_URL)
                                            (p/request ACCEPT_INVITATION_URL 
                                                       :request-method :post)
                                            p/follow-redirect)]
                 (:uri request)) => (contains OBJECTIVE_URL)
               (provided
                 (http-api/post-candidate-writer {:invitee-id USER_ID
                                                  :invitation-uuid UUID
                                                  :objective-id OBJECTIVE_ID}) => {:status ::http-api/success
                                                                                   :result {}}))

         (fact "a user is granted writer-for-OBJECTIVE_ID role when accepting an invitation"
               (against-background
                 (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                                 :result ACTIVE_INVITATION}
                 (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                           :result {:title OBJECTIVE_TITLE}})

               (-> user-session
                   (helpers/with-sign-in "http://localhost:8080/")
                   (p/request INVITATION_URL)
                   (p/request ACCEPT_INVITATION_URL :request-method :post)) => anything

               (provided
                 (http-api/post-candidate-writer {:invitee-id USER_ID
                                                  :invitation-uuid UUID
                                                  :objective-id OBJECTIVE_ID})
                 => {:status ::http-api/success
                     :result {}}
                 (utils/add-authorisation-role anything WRITER_ROLE_FOR_OBJECTIVE) => {}))


         (fact "a user cannot accept an invitation without invitation credentials"
               (let [peridot-response (-> user-session
                                          (helpers/with-sign-in "http://localhost:8080/")
                                          (p/request ACCEPT_INVITATION_URL 
                                                     :request-method :post))]
                 peridot-response => (contains {:response (contains {:status 401})})))))

(binding [config/enable-csrf false]
  (facts "declining an invitation"
         (fact "a user can decline an invitation when they have invitation credentials"
               (against-background
                 (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                                 :result ACTIVE_INVITATION})
               (let [peridot-response (-> user-session
                                          (p/request INVITATION_URL)
                                          (p/request DECLINE_INVITATION_URL
                                                     :request-method :post)
                                          p/follow-redirect)]  
                 peridot-response) => (contains {:request (contains {:uri "/"})})
               (provided
                 (http-api/decline-invitation {:invitation-uuid UUID
                                               :objective-id OBJECTIVE_ID
                                               :invitation-id INVITATION_ID}) => {:status ::http-api/success
                                                                                  :result {}}))

         (fact "a user cannot decline an invitation without invitation credentials"
               (-> (p/request user-session DECLINE_INVITATION_URL :request-method :post)
                   (get-in [:response :status])) => 401)))
