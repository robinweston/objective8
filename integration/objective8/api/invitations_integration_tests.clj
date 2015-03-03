(ns objective8.api.invitations-integration-tests
  (:require [peridot.core :as p]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [objective8.integration-helpers :as helpers]
            [objective8.storage-helpers :as sh]
            [objective8.writers :as writers]
            [objective8.users :as users]
            [objective8.objectives :as objectives]
            [objective8.middleware :as m])) 


;; Testing from http request -> making correct calls within writers namespace
;; Mock or stub out 'writers' namespace

(def app (helpers/test-context))
(def OBJECTIVE_ID 1)
(def INVITED_BY_ID 2)
(def INVITATION_ID 3)
(def the-invitation {:writer-name "Mel"
                     :reason "She's cool"
                     :objective-id OBJECTIVE_ID
                     :invited-by-id INVITED_BY_ID })
(def the-stored-invitation (assoc the-invitation :_id INVITATION_ID))
(def the-invitation-as-json (str "{\"writer-name\":\"Mel\",\"reason\":\"She's cool\",\"objective-id\":" OBJECTIVE_ID ",\"invited-by-id\":" INVITED_BY_ID "}"))

(facts "about inviting policy writers" :integration
       (against-background
         (m/valid-credentials? anything anything anything) => true)
       (fact "the invitation is stored"
             (p/request app (str "/api/v1/objectives/" OBJECTIVE_ID "/writers/invitations")
                        :request-method :post
                        :content-type "application/json"
                        :body the-invitation-as-json) => (helpers/check-json-body the-stored-invitation)
             (provided
               (writers/store-invitation! the-invitation) => the-stored-invitation))

       (fact "the http response indicates the location of the invitation"
             (against-background
               (writers/store-invitation! anything) => the-stored-invitation)

             (let [result (p/request app (str "/api/v1/objectives/" OBJECTIVE_ID 
                                              "/writers/invitations")
                                     :request-method :post
                                     :content-type "application/json"
                                     :body the-invitation-as-json)
                   response (:response result)
                   headers (:headers response)]
               response => (contains {:status 201})
               headers => (contains {"Location" (contains 
                                                  (str "/api/v1/objectives/" OBJECTIVE_ID 
                                                       "/writers/invitations/" INVITATION_ID))})))
       
       (fact "a 400 status is returned if a PSQLException is raised"
             (against-background
               (writers/store-invitation! anything) =throws=> (org.postgresql.util.PSQLException.
                                                                  (org.postgresql.util.ServerErrorMessage. "" 0)))
             (:response (p/request app (str "/api/v1/objectives/" OBJECTIVE_ID "/writers/invitations")
                                   :request-method :post
                                   :content-type "application/json"
                                   :body the-invitation-as-json)) => (contains {:status 400})))

(facts "invitations" :integration
       (against-background
         [(m/valid-credentials? anything anything anything) => true 
          (before :contents (do
                              (helpers/db-connection)
                              (helpers/truncate-tables)))
          (after :facts (helpers/truncate-tables)) ]

         (facts "GET /api/v1/invitations?uuid=<UUID>"
                (fact "retrieves the active invitation with the given uuid if it exists"
                      (let 
                        [created-by-id (:_id (users/store-user! {:twitter-id "some-twitter-id" :username "username"}))
                         objective-id (:_id (objectives/store-objective! {:created-by-id created-by-id :end-date "2015-01-01"}))
                         stored-invitation (writers/store-invitation! {:invited-by-id created-by-id 
                                                                       :objective-id objective-id})
                         uuid (:uuid stored-invitation)]
                        (helpers/peridot-response-json-body->map (p/request app (str "/api/v1/invitations?uuid=" uuid))) => (dissoc stored-invitation :entity)))

                (fact "returns a 404 status if an invitation with uuid=<UUID> doesn't exist"
                      (p/request app "/api/v1/invitations?uuid=non-existent-uuid") => (contains {:response (contains {:status 404})}))

                (fact "returns a 400 status when a PSQLException is raised"
                      (against-background
                        (writers/retrieve-invitation-by-uuid anything) =throws=> (org.postgresql.util.PSQLException.
                                                                                   (org.postgresql.util.ServerErrorMessage. "" 0)))
                      (p/request app "/api/v1/invitations?uuid=some-uuid") => (contains {:response (contains {:status 400})})))))

(facts "accepting-invitations" :integration
     (against-background
       [(m/valid-credentials? anything anything anything) => true 
          (before :contents (do
                              (helpers/db-connection)
                              (helpers/truncate-tables)))
          (after :facts (helpers/truncate-tables)) ]

       (facts "POST /api/v1/objectives/:obj-id/candidate-writers"
              (fact "creating a candidate writer accepts the invitation"
                    (let [{invitation-id :_id
                           objective-id :objective-id
                           invitation-reason :reason
                           writer-name :name
                           invitation-uuid :uuid} (sh/store-an-invitation)

                         {invitee-id :_id} (users/store-user! {:twitter-id "some-other-twitter-id" :username "otherUsername"})
                         candidate-data-as-json (json/generate-string {:invitation-uuid invitation-uuid
                                                                       :user-id invitee-id
                                                                       :objective-id objective-id})
                         {response :response} (p/request app (str "/api/v1/objectives/" objective-id
                                                                  "/candidate-writers")
                                                         :request-method :post
                                                         :content-type "application/json"
                                                         :body candidate-data-as-json)
                         updated-invitation (writers/retrieve-invitation invitation-id)]
                     (:status updated-invitation) => "accepted"
                     (:status response) => 201
                     (:headers response) => (helpers/location-contains (str "/api/v1/objectives/" objective-id
                                                                            "/candidate-writers/"))
                     (:body response) => (helpers/json-contains {:_id integer?
                                                                 :user-id invitee-id
                                                                 :invitation-id invitation-id
                                                                 :objective-id objective-id
                                                                 :invitation-reason invitation-reason
                                                                 :writer-name writer-name})))

              (fact "Cannot create a candidate writer when no invitation exists with given uuid"
                    (let [{invitee-id :_id} (sh/store-a-user)
                          {objective-id :_id} (sh/store-an-objective)
                          candidate-data-as-json (json/generate-string {:invitation-uuid "nonexistent uuid"
                                                                        :user-id invitee-id
                                                                        :objective-id objective-id
                                                                        :invitation-reason "some reason"
                                                                        :writer-name "writer name"})
                          {response :response} (p/request app (str "/api/v1/objectives/" objective-id
                                                                   "/candidate-writers")
                                                          :request-method :post
                                                          :content-type "application/json"
                                                          :body candidate-data-as-json)]
                      (:status response) => 403)))))
