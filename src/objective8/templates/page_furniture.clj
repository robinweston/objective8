(ns objective8.templates.page-furniture
  (:require [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [objective8.config :as config]
            [objective8.utils :as utils]))

(def library-html "templates/jade/library.html")
(def library-html-resource (html/html-resource library-html {:parser jsoup/parser}))

(defn translator
  "Returns a translation function which replaces the
   content of nodes with translations for k"
  [{:keys [translations] :as context}]
  (fn [k] 
    #(assoc % :content (translations k))))

(defn text->p-nodes
  "Turns text into a collection of paragraph nodes based on linebreaks.
   Returns nil if no text is supplied"
  [text]
  (when text
    (let [newline-followed-by-optional-whitespace #"(\n+|\r+)\s*"]
    (map (fn [p] (html/html [:p p])) (clojure.string/split text
                                                           newline-followed-by-optional-whitespace)))))

(def anchor-button (html/select library-html-resource [:.clj-anchor-button]))

;; GOOGLE ANALYTICS

(def google-analytics-script (html/select library-html-resource [:.clj-google-analytics]))

(defn add-google-analytics [nodes] 
  (if-let [tracking-id (config/get-var "GA_TRACKING_ID")] 
    (html/at nodes 
             [:head] (html/append google-analytics-script)
             [:head html/any-node] (html/replace-vars {:trackingID tracking-id}))
    nodes))


;; MASTHEAD

(def masthead-snippet (html/select library-html-resource [:.clj-masthead-signed-out])) 
(def masthead-signed-in-snippet (html/select library-html-resource [:.clj-masthead-signed-in]))

(defn masthead [{{uri :uri} :ring-request :keys [translations  user] :as context}]
  (let [tl8 (translator context)]
    (html/at masthead-snippet
             [:.clj-masthead-signed-out] (if user
                                           (html/substitute masthead-signed-in-snippet)
                                           identity)
             [:.clj-masthead-skip-text] (tl8 :masthead/skip-to-navigation)
             [:.clj-masthead-logo] (html/set-attr "title" (translations :masthead/logo-title-attr))
             [:.clj-masthead-objectives-link] (html/do->
                                                (html/set-attr "title" (translations :masthead/objectives-link-title-attr))
                                                (tl8 :masthead/objectives-link))
             [:.clj-masthead-about-link] (html/do->
                                           (html/set-attr "title" (translations :masthead/about-link-title-attr))
                                           (tl8 :masthead/about-link))
             [:.clj-masthead-sign-in] (html/set-attr "title" (translations :navigation-global/sign-in-title))
             [:.clj-masthead-sign-in] (html/set-attr "href" (str "/sign-in?refer=" uri))
             [:.clj-masthead-sign-in-text] (tl8 :navigation-global/sign-in-text)
             [:.clj-masthead-sign-out] (html/set-attr "title" (translations :navigation-global/sign-out-title))
             [:.clj-masthead-sign-out-text] (tl8 :navigation-global/sign-out-text)
             [:.clj-username] (html/content (:username user)))))

;; STATUS BAR

(def flash-bar-snippet (first (html/select library-html-resource [:.clj-flash-message-bar]))) 

(html/defsnippet invitation-response-banner library-html [:.clj-invitation-response-link]
  [invitation-rsvp translations]
  [:.clj-invitation-response-link] 
  (html/do->
    (html/set-attr :href (utils/local-path-for :fe/objective :id (:objective-id invitation-rsvp)))
    (html/content (translations :invitation-response/banner-message))))

(defn flash-bar [flash] 
  (html/at flash-bar-snippet
           [:.clj-flash-message-bar-text] (html/content flash)))

(html/defsnippet status-flash-bar
  library-html [:.clj-status-bar] [{:keys [doc translations invitation-rsvp] :as context}]
  [:.clj-status-bar] (cond
                       (:flash doc) (html/substitute (flash-bar (:flash doc)))
                       invitation-rsvp (html/substitute (flash-bar (invitation-response-banner invitation-rsvp translations)))
                       :else identity)
  [:.clj-status-bar-text] (html/content (translations :status-bar/status-text)))


;; DRAFTING HAS STARTED MESSAGE

(html/defsnippet drafting-message library-html [:.clj-drafting-message] [{{objective :objective} :data
                                                                          translations :translations
                                                                          :as context}]
  [html/any-node] (when (:drafting-started objective) identity)
  [:.clj-drafting-message-title] (html/content (translations :notifications/drafting-message-title))
  [:.clj-drafting-message-body] (html/content (translations :notifications/drafting-message-body))
  [:.clj-drafting-message-link] (html/do->
                                  (html/set-attr "href" (str "/objectives/" (:_id objective) "/drafts"))
                                  (html/content (translations :notifications/drafting-message-link))))

;; WRITER LIST


(html/defsnippet invite-writer-form
  library-html [:.clj-invite-a-writer-form] [{:keys [translations data]}]
  [:.clj-invite-a-writer-form] (html/prepend (html/html-snippet (anti-forgery-field)))
  [:.l8n-label-writer-name] (html/content (translations :invite-writer/writer-name-label))
  [:.l8n-input-writer-name] (html/set-attr "title" (translations :invite-writer/writer-name-attr-title))
  [:.l8n-label-writer-email] (html/content (translations :invite-writer/writer-email-label))
  [:.l8n-input-writer-email] (html/set-attr "title" (translations :invite-writer/writer-email-attr-title))
  [:.l8n-label-writer-reason] (html/content (translations :invite-writer/writer-reason-label))
  [:.l8n-input-writer-reason] (html/set-attr "title" (translations :invite-writer/writer-reason-attr-title))
  [:.l8n-button-invite-writer] (html/content (translations :invite-writer/invite-button)))

(html/defsnippet sign-in-to-invite-writer
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.l8n-before-link] (html/content (translations :invite-writer/sign-in-please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :invite-writer/sign-in)))
  [:.l8n-after-link] (html/content (translations :invite-writer/sign-in-to)))

(defn invite-writer [{user :user :as context}]
  (if user
    (invite-writer-form context)
    (sign-in-to-invite-writer context)))

(html/defsnippet empty-writer-list-item
  library-html [:.clj-empty-writer-list-item] [{translations :translations}]
  [:.clj-empty-writer-list-item] (html/content (translations :candidate-list/no-candidates)))

(html/defsnippet writer-list-items
  library-html [:.clj-writer-item-without-photo] [candidates]
  [:.clj-writer-item-without-photo :a] nil
  [:.clj-writer-item-without-photo] (html/clone-for [candidate candidates]
                                                    [:.clj-writer-name] (html/content (:writer-name candidate))
                                                    [:.clj-writer-description] (html/content (:invitation-reason candidate))))

(defn writer-list [context]
  (let [candidates (get-in context [:data :candidates])]
    (if (empty? candidates)
      (empty-writer-list-item context)
      (writer-list-items candidates))))
;; ANSWER LIST

(html/defsnippet sign-in-to-add-answer
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request] :as context}]
  [:.l8n-before-link] (html/content (translations :answer-sign-in/please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :answer-sign-in/sign-in)))
  [:.l8n-after-link] (html/content (translations :answer-sign-in/to)))

;; QUESTION LIST

(html/defsnippet empty-question-list-item
  library-html [:.clj-empty-question-list-item] [{translations :translations}]
  [:.clj-empty-question-list-item] (html/content (translations :question-list/no-questions)))

(html/defsnippet question-list-items
  library-html [:.clj-question-item] [questions translations]
  [:.clj-question-item] (html/clone-for [question questions]
                                        [:.clj-question-text] (html/content (:question question))
                                        [:.clj-answer-link] (html/do->
                                                              (html/content (translations :objective-view/answer-link))
                                                              (html/set-attr "href" (str "/objectives/" (:objective-id question)
                                                                                         "/questions/" (:_id question))))))

(defn question-list [{translations :translations :as context}]
  (let [questions (get-in context [:data :questions])]
    (if (empty? questions)
      (empty-question-list-item context)
      (question-list-items questions translations))))

(html/defsnippet add-question-form
  library-html [:.clj-question-create-form] [{:keys [translations data]}]
  [:.clj-question-create-form] (html/prepend (html/html-snippet (anti-forgery-field)))

  [:.l8n-label-add-question] (html/content (translations :question-create/question-label))
  [:.l8n-textarea-add-question] (html/set-attr "title" (translations :question-create/question-title))
  [:.l8n-button-add-question] (html/content (translations :question-create/post-button)))

(html/defsnippet sign-in-to-add-question
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.l8n-before-link] (html/content (translations :question-sign-in/please))
  [:.l8n-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request)))
                         (html/content (translations :question-sign-in/sign-in)))
  [:.l8n-after-link] (html/content (translations :question-sign-in/to)))

(defn add-question [{user :user :as context}]
  (if user
    (add-question-form context)
    (sign-in-to-add-question context)))

;; COMMENT LIST

(defn voting-actions-when-signed-in [{:keys [data ring-request] :as context} comment]
  (html/transformation
   [:.clj-up-down-vote-form] (html/prepend (html/html-snippet (anti-forgery-field)))
   [:.clj-vote-on-uri] (html/set-attr "value" (:uri comment))
   [:.clj-refer] (html/set-attr "value" (str (:uri ring-request) "#comments"))
   [:.clj-up-vote-count] (html/content (str (get-in comment [:votes :up])))
   [:.clj-down-vote-count] (html/content (str (get-in comment [:votes :down])))))

(defn voting-actions-when-not-signed-in [{:keys [data ring-request] :as context} comment]
  (html/transformation
   [:.clj-up-down-vote-form] (html/set-attr "method" "get")
   [:.clj-up-down-vote-form] (html/set-attr "action" "/sign-in")
   [:.clj-refer] (html/set-attr "value" (str (:uri ring-request) "#comments"))
   [:.clj-vote-on-uri] nil
   [:.clj-up-vote-count] (html/content (str (get-in comment [:votes :up])))
   [:.clj-down-vote-count] (html/content (str (get-in comment [:votes :down])))))

(html/defsnippet empty-comment-list-item
  library-html [:.clj-empty-comment-list-item] [translations]
  [:.clj-empty-comment-list-item] (html/content (translations :comment-view/no-comments)))

(html/defsnippet comment-list-items
  library-html [:.clj-comment-item] [{:keys [data ring-request user] :as context}]
  [:.clj-comment-item] (html/clone-for [comment (:comments data)]
                                       [:.clj-comment-author] (html/content (:username comment))
                                       [:.clj-comment-date] (html/content (utils/iso-time-string->pretty-time (:_created_at comment)))
                                       [:.clj-comment-text] (html/content (:comment comment))
                                       [:.clj-up-down-vote-form] (if user
                                                                     (voting-actions-when-signed-in context comment)
                                                                     (voting-actions-when-not-signed-in context comment))
                                       [:.clj-comment-reply] nil))


(defn comment-list [{translations :translations :as context}]
  (let [comments (get-in context [:data :comments])]
    (if (empty? comments)
      (empty-comment-list-item translations)
      (comment-list-items context))))

;; COMMENT CREATE

(html/defsnippet comment-create-form
  library-html [:.clj-add-comment-form] [{:keys [translations data ring-request]} comment-target]
  [:.clj-add-comment-form] (html/prepend (html/html-snippet (anti-forgery-field)))
  [:.clj-refer] (html/set-attr "value" (:uri ring-request))
  [:.clj-comment-on-uri] (html/set-attr "value" (get-in data [comment-target :uri]))
  [:.clj-add-comment] (html/content (translations :comment-create/post-button)))

(html/defsnippet sign-in-to-comment
  library-html [:.clj-please-sign-in] [{:keys [translations ring-request]}]
  [:.clj-before-link] (html/content (translations :comment-sign-in/please))
  [:.clj-sign-in-link] (html/do->
                         (html/set-attr "href" (str "/sign-in?refer=" (:uri ring-request) "%23comments"))
                         (html/content (translations :comment-sign-in/sign-in)))
  [:.clj-after-link] (html/content (translations :comment-sign-in/to)))

(defn comment-create [{user :user :as context} comment-target]
  (if user
    (comment-create-form context comment-target)
    (sign-in-to-comment context)))
