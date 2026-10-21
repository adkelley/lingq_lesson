(ns lingq-lesson.openai)

(defn api-key [] (System/getenv "OPENAI_API_KEY"))
(def speech-url "https://api.openai.com/v1/audio/speech")
(def responses-url "https://api.openai.com/v1/responses")

(defn require-api-key []
  (when-not (seq (api-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- api-error-message [parsed-body]
  (or (get-in parsed-body [:error :message])
      (:message parsed-body)))

(defn status-message
  ([status] (status-message status nil))
  ([status parsed-body]
   (let [message (case status
                   429 "OpenAI returned status 429. Check or update your OpenAI billing and usage limits."
                   401 "OpenAI returned status 401. Check that OPENAI_API_KEY is set and valid."
                   403 "OpenAI returned status 403. Your API key does not have access to this resource."
                   (str "OpenAI returned status " status "."))]
     (cond-> message
       (api-error-message parsed-body)
       (str " " (api-error-message parsed-body))))))
