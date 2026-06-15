(ns lingq-lesson.audio
  (:require
   [hato.client :as hc]
   [clojure.string :as string]
   [cheshire.core :as cheshire]))

(import
 '[java.nio.charset StandardCharsets])

(defn- openai-key [] (System/getenv "OPENAI_API_KEY"))
(def openai-speech-url "https://api.openai.com/v1/audio/speech")

(def voices #{"alloy" "ash" "ballad" "cedar" "coral" "echo"
              "fable" "marin" "nova" "onyx" "sage" "verse"})

(def supported-audio-formats #{"mp3" "wav"})

(def ^:private newscaster-instructions
  (str
   "Voice: Clear, confident, and professional, with a well-supported mid-to-deep "
   "register. Speech is articulate and steady, conveying credibility and authority."
   "\n"
   "Phrasing: Sentences flow smoothly and logically, with emphasis placed on key facts, "
   "names, locations, and developments. Information is presented efficiently and without "
   "unnecessary dramatization."
   "\n"
   "Punctuation: Natural pauses at commas and sentence boundaries. Brief pauses separate "
   "topics and transitions. Avoid excessive hesitation, ellipses, or dramatic stops."
   "\n"
   "Tone: Objective, composed, and informative. Maintain a calm, measured delivery that "
   "prioritizes clarity, accuracy, and listener comprehension while remaining engaging and attentive."))

(def ^:private model "gpt-4o-mini-tts")

(def default-tts-opts {:model model
                       :voice "cedar"
                       :format "mp3"
                       :vibe "newscaster"})

;; TTS
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
      (cheshire/decode body keyword))))

(defn- encode [value]
  (cheshire/encode value))

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

(defn- status-message [status]
  (case status
    429 "OpenAI returned status 429. Check or update your OpenAI billing and usage limits."
    401 "OpenAI returned status 401. Check that OPENAI_API_KEY is set and valid."
    403 "OpenAI returned status 403. Your API key does not have access to this resource."
    (str "OpenAI returned status " status ".")))

(defn- throw-response-error [response request-headers]
  (let [{:keys [status body headers]} response
        parsed-body (parse-json-body body)]

    (throw
     (ex-info (status-message status)
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

(defn- request-headers [content-type]
  (cond-> {"authorization" (format "Bearer %s" (openai-key))}
    content-type (assoc "content-type" content-type)))

(defn- vibe->instructions [vibe]
  (case vibe
    "newscaster" newscaster-instructions
    "default" newscaster-instructions))

(defn text-to-speech! [text opts]
  (require-api-key)
  (let [request-opts (merge default-tts-opts opts)
        instructions (vibe->instructions (:vibe request-opts))
        payload (cond-> {:model (:model request-opts)
                         :input text
                         :voice (:voice request-opts)
                         :response_format (:format request-opts)}
                  (seq instructions)
                  (assoc :instructions instructions))
        headers (request-headers "application/json")
        response
        (try
          (hc/post openai-speech-url
                   {:headers headers
                    :body (encode payload)
                    :as :byte-array
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI audio speech request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]} response]
    (when-not (<= 200 status 299)
      (throw-response-error (assoc response :body (body->string body))
                            headers))
    body))
