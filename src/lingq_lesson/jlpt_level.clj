(ns lingq-lesson.jlpt-level
  (:require
   [hato.client :as hc]
   [clojure.string :as string]
   [cheshire.core :as json]))

(import
 '[java.nio.charset StandardCharsets])

(defn- openai-key [] (System/getenv "OPENAI_API_KEY"))
(def model "gpt-5-mini")

(defn- body->string [body]
  (cond
    (string? body) body
    (instance? (Class/forName "[B") body)
    (String. ^bytes body StandardCharsets/UTF_8)
    :else
    (some-> body str)))

(defn- parse-json-body [body]
  (let [body (body->string body)]
    (when (seq body)
      (json/decode body keyword))))

(defn- encode [value]
  (json/encode value))

(defn- normalize-header-key [k]
  (string/lower-case (name k)))

(defn- normalize-headers [headers]
  (into {}
        (map (fn [[k v]]
               [(normalize-header-key k) v]))
        headers))

(defn- redact-headers [headers]
  (let [headers (normalize-headers headers)]
    (cond-> headers
      (contains? headers "authorization")
      (assoc "authorization" "[REDACTED]"))))

(defn- api-error-message [parsed-body]
  (or (get-in parsed-body [:error :message])
      (:message parsed-body)))

(defn- status-message [status parsed-body]
  (let [message (case status
                  429 "OpenAI returned status 429. Check or update your OpenAI billing and usage limits."
                  401 "OpenAI returned status 401. Check that OPENAI_API_KEY is set and valid."
                  403 "OpenAI returned status 403. Your API key does not have access to this resource."
                  (str "OpenAI returned status " status "."))]
    (cond-> message
      (api-error-message parsed-body)
      (str " " (api-error-message parsed-body)))))

(defn- throw-response-error [response request-headers]
  (let [{:keys [status body headers]} response
        parsed-body (parse-json-body body)]

    (throw
     (ex-info (status-message status parsed-body)
              {:status status
               :headers headers
               :body parsed-body
               :response {:status status
                          :headers headers
                          :body body
                          :opts {:headers (redact-headers request-headers)}}}))))

(defn- require-api-key []
  (when-not (seq (openai-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- request-headers []
  {"content-type" "application/json"
   "authorization" (format "Bearer %s" (openai-key))})

(defn- post-responses-request [payload]
  (when-not (seq (openai-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {})))

  (let [headers (request-headers)
        response
        (try
          (hc/post "https://api.openai.com/v1/responses"
                   {:headers headers
                    :body (json/encode payload)
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]}
        response
        parsed-body
        (parse-json-body body)]
    (when-not (<= 200 status 299)
      (throw-response-error response headers))
    (when-not parsed-body
      (throw (ex-info "OpenAI response body was empty or could not be decoded."
                      {:status status
                       :body body
                       :response {:status status
                                  :body body
                                  :opts {:headers (redact-headers headers)}}})))
    parsed-body))

(defn prompt [article-text]
  (str
   "You are an expert Japanese language instructor and JLPT evaluator.\n\n"
   "Analyze the following Japanese article and estimate its reading difficulty according to the JLPT scale (N5, N4, N3, N2, N1).\n\n"
   "Consider all of the following factors:\n\n"

   "1. Vocabulary Difficulty\n"
   "    * Frequency of common versus rare words\n"
   "    * Presence of N5, N4, N3, N2, and N1 vocabulary\n"
   "    * Presence of technical, academic, legal, business, or specialized terminology\n"
   "2. Grammar Difficulty\n"
   "    * Complexity of grammatical constructions\n"
   "    * Frequency of advanced grammar patterns\n"
   "    * Degree of implicit meaning requiring inference\n"
   "3. Kanji Difficulty\n"
   "    * Density of kanji\n"
   "    * Presence of uncommon or advanced kanji\n"
   "    * Use of proper nouns and specialized terminology\n"
   "4. Sentence Complexity\n"
   "    * Average sentence length\n"
   "    * Number of subordinate clauses\n"
   "    * Degree of nesting and syntactic complexity\n"
   "5. Domain Knowledge\n"
   "    * Whether understanding requires specialized knowledge\n"
   "    * Whether the text is accessible to a general reader\n\n"

   "Return your assessment in the following JSON format:\n\n"
   "{\n"
   "    \"jlpt-level\": \"N3\",\n"
   "    \"lingq-level\": 3,\n"
   "    \"confidence\": 87,\n"
   "    \"vocabulary-level\": \"N3\",\n"
   "    \"grammar-level\": \"N2\",\n"
   "    \"kanji-level\": \"N3\",\n"
   "    \"sentence-complexity\": \"Moderate\",\n"
   "    \"domain-difficulty\": \"General\",\n"
   "    \"justification\": \"Most vocabulary is common and appears in JLPT N3 materials. Several N2 grammar structures appear, but sentences remain relatively short and accessible.\"\n"
   "}\n\n"

   "Map JLPT levels to LingQ levels as follows:\n\n"
   "N5 -> 1\n"
   "N4 -> 2\n"
   "N3 -> 3\n"
   "N2 -> 4\n"
   "N1 -> 5\n\n"

   "Use LingQ level 6 only when the material significantly exceeds typical JLPT N1 difficulty, such as:\n\n"
   "    * Academic papers\n"
   "    * Legal documents\n"
   "    * Technical manuals\n"
   "    * Literary prose with highly sophisticated language\n"
   "    * Graduate-level or professional publications\n\n"
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

(defn article-text->jlpt-level!
  ([article-text] (article-text->jlpt-level! article-text false))
  ([article-text verbose]
   (require-api-key)
   (let [response (post-responses-request (responses-payload article-text))
         output-text (response->output-text response)]
     (when-not (seq output-text)
       (throw (ex-info "OpenAI response did not include output text."
                       {:response response})))
     (when verbose
       (println "JLPT level response:" output-text))
     (json/decode output-text keyword))))
