(ns lingq-lesson.openai.article-classifier
  (:require
   [hato.client :as hc]
   [lingq-lesson.api :as api]
   [lingq-lesson.openai :as openai]
   [lingq-lesson.openai.jlpt-level-classifier :as jlpt-level-classifier]
   [lingq-lesson.openai.category-classifier :as category-classifier]))

(def decisions-url "https://api.openai.com/v1/decisions")
(def model "gpt-6-luna")

(defn questions
  [article-text questions]

  (openai/require-api-key)

  (let [request-headers {"content-type" "application/json"
                         "authorization" (str "Bearer " (openai/api-key))}
        response
        (try
          (hc/post decisions-url
                   {:headers request-headers
                    :body (api/encode
                           {:model model
                            :input article-text
                            :questions questions})
                    :throw-exceptions false})
          (catch Exception error
            (throw (ex-info "OpenAI request failed."
                            {:error (.getMessage error)}
                            error))))
        {:keys [body headers status]}
        response]
    (when-not (<= 200 status 299)
      (api/throw-response-error response request-headers openai/status-message))
    (let [parsed-body (api/parse-json-body body)]
      (when-not parsed-body
        (throw (ex-info "Typesafe response body was empty or could not be decoded."
                        {:status status
                         :headers headers
                         :body body
                         :response {:status status
                                    :headers headers
                                    :body body
                                    :opts {:headers (api/redact-headers request-headers)}}})))
      parsed-body)))

(defn- answer->choice
  [answer]
  (case (:type answer)
    "choice" (:choice answer)
    "refusal" (throw (ex-info "OpenAI declined to classify the article."
                              {:question (:name answer)}))
    (throw (ex-info "Unexpected OpenAI answer type."
                    {:question (:name answer)
                     :type (:type answer)}))))

(defn article-text->classification!
  [article-text]
  (let [response (questions
                  article-text
                  [jlpt-level-classifier/jlpt-level-question
                   category-classifier/category-question])
        [jlpt-level-answer category-answer] (:answers response)
        jlpt-level-choice (answer->choice jlpt-level-answer)
        category-choice (answer->choice category-answer)]
    {:jlpt-level jlpt-level-choice
     :category category-choice}))
