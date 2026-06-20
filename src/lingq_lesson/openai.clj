(ns lingq-lesson.openai
  (:require
   [clojure.string :as string]
   [cheshire.core :as json]))

(import
 '[java.nio.charset StandardCharsets])

(defn api-key [] (System/getenv "OPENAI_API_KEY"))
(def speech-url "https://api.openai.com/v1/audio/speech")
(def responses-url "https://api.openai.com/v1/responses")

(defn require-api-key []
  (when-not (seq (api-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn request-headers [content-type]
  (cond-> {"authorization" (format "Bearer %s" (api-key))}
    content-type (assoc "content-type" content-type)))

(defn body->string [body]
  (cond
    (string? body) body
    (instance? (Class/forName "[B") body)
    (String. ^bytes body StandardCharsets/UTF_8)
    :else
    (some-> body str)))

(defn parse-json-body [body]
  (let [body (body->string body)]
    (when (seq body)
      (json/decode body keyword))))

(defn encode [value]
  (json/encode value))

(defn decode [body]
  (parse-json-body body))

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

(defn- normalize-header-key [k]
  (string/lower-case (name k)))

(defn- normalize-headers [headers]
  (into {}
        (map (fn [[k v]]
               [(normalize-header-key k) v]))
        headers))

(defn redact-headers [headers]
  (let [headers (normalize-headers headers)]
    (cond-> headers
      (contains? headers "authorization")
      (assoc "authorization" "[REDACTED]"))))

(defn throw-response-error [response request-headers]
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
