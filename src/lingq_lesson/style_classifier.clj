(ns lingq-lesson.style-classifier
  (:require
   [hato.client :as hc]
   [lingq-lesson.openai :as openai]))

(def model "gpt-5-mini")

(defn- post-responses-request [payload]
  (openai/require-api-key)
  (let [headers (openai/request-headers "application/json")
        response
        (try
          (hc/post openai/responses-url
                   {:headers headers
                    :body (openai/encode payload)
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]}
        response
        parsed-body
        (openai/parse-json-body body)]
    (when-not (<= 200 status 299)
      (openai/throw-response-error response headers))
    (when-not parsed-body
      (throw (ex-info "OpenAI response body was empty or could not be decoded."
                      {:status status
                       :body body
                       :response {:status status
                                  :body body
                                  :opts {:headers (openai/redact-headers headers)}}})))
    parsed-body))

(defn prompt [article-text]
  (str
   "You are an article style classifier."
   "\n"
   "Analyze the provided article and determine its primary editorial category. The article may be written in any language."
   "\n"
   "Classify the article into exactly one of the following categories:"
   "\n"
   "  * business\n"
   "  * crime\n"
   "  * entertainment\n"
   "  * sports\n"
   "  * technology\n"
   "  * lifestyle\n"
   "  * news\n\n"

   "If the article is not clear or does not fit into any of the above categories, then classify it as \"other\"."

   "Return your assessment in the following JSON format:\n\n"
   "{\n"
   "    \"vibe\": \"business\",\n"
   "    \"confidence\": 87,\n"
   "    \"sentence-complexity\": \"Moderate\",\n"
   "    \"justification\": \"Most vocabulary is common to business, such as company names and industry terminology.\"\n"
   "}\n\n"

   "Provide only valid JSON.\n\n"
   "Article:\n"
   "<article>\n"
   article-text
   "\n</article>"))

(defn- responses-payload [article-text]
  {:model model
   :input (prompt article-text)
   :text {:format {:type "json_object"}}})

(defn- content-text [content]
  (some (fn [{:keys [type text]}]
          (when (= "output_text" type)
            text))
        content))

(defn- response->output-text [response]
  (or (:output_text response)
      (some (fn [{:keys [content]}]
              (content-text content))
            (:output response))))

(defn article-text->style!
  ([article-text] (article-text->style! article-text false))
  ([article-text verbose]
   (let [response (post-responses-request (responses-payload article-text))
         output-text (response->output-text response)]
     (when-not (seq output-text)
       (throw (ex-info "OpenAI response did not include output text."
                       {:response response})))
     (when verbose
       (println "Style classifier response:" output-text))
     (openai/decode output-text))))
