(ns lingq-lesson.openai-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [lingq-lesson.api :as api]
   [lingq-lesson.openai :as openai]))

(deftest redact-headers-normalizes-and-redacts-authorization
  (is (= {"authorization" "[REDACTED]"
          "content-type" "application/json"}
         (api/redact-headers {"Authorization" "Bearer secret"
                              :content-type "application/json"}))))

(deftest throw-response-error-includes-openai-error-message
  (try
    (api/throw-response-error
     {:status 400
      :headers {}
      :body "{\"error\":{\"message\":\"Invalid request\"}}"}
     {"authorization" "Bearer secret"}
     openai/status-message)
    (is false "Expected OpenAI response error")
    (catch clojure.lang.ExceptionInfo e
      (is (string/includes? (.getMessage e)
                            "OpenAI returned status 400. Invalid request")))))

(deftest throw-response-error-redacts-request-authorization
  (try
    (api/throw-response-error
     {:status 401
      :headers {}
      :body "{\"error\":{\"message\":\"Bad key\"}}"}
     {"authorization" "Bearer secret"} openai/status-message)
    (is false "Expected OpenAI response error")
    (catch clojure.lang.ExceptionInfo e
      (is (= "[REDACTED]"
             (get-in (ex-data e) [:response :opts :headers "authorization"])))
      (is (= "Bad key"
             (get-in (ex-data e) [:body :error :message]))))))

(deftest body->string-decodes-byte-arrays
  (is (= "{\"error\":{\"message\":\"Invalid request\"}}"
         (api/body->string
          (.getBytes "{\"error\":{\"message\":\"Invalid request\"}}" "UTF-8")))))
