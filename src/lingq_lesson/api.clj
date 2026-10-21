(ns lingq-lesson.api
  (:require
   [clojure.string :as string]
   [cheshire.core :as json]))

(import
 '[java.nio.charset StandardCharsets])

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

(defn request-headers [api-key content-type]
  (cond-> {"authorization" (format "Bearer %s" api-key)}
    content-type (assoc "content-type" content-type)))

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

(defn throw-response-error [response request-headers status-message-fn]
  (let [{:keys [status body headers]} response
        parsed-body (parse-json-body body)]

    (throw
     (ex-info (status-message-fn status parsed-body)
              {:status status
               :headers headers
               :body parsed-body
               :response {:status status
                          :headers headers
                          :body body
                          :opts {:headers (redact-headers request-headers)}}}))))
