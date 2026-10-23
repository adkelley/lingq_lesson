(ns lingq-lesson.typesafe
  (:require
   [clojure.string :as string]
   [hato.client :as hc]
   [lingq-lesson.api :as api]))

(def model "jev-latest")
(def api-key (System/getenv "TYPESAFE_API_KEY"))
(def url "https://api.typesafe.ai/v1/systemone")

(defn status-message [status]
  (case status
    401 "TypeSafe returned status 401. Check that TYPESAFE_API_KEY is set and valid."
    413 "TypeSafe returned status 413. The request payload is too large (> 32MB)."
    415 "TypeSafe returned status 415. The request payload media type is not supported."
    422 "TypeSafe returned status 422. The request body failed validation."
    429 "TypeSafe returned status 429. You have exceeded your rate limit. Back off and try again later."
    529 "TypeSafe returned status 529. The server is overloaded. Try again later."
    (str "TypeSafe returned status " status ".")))

(defn require-api-key []
  (when (string/blank? api-key)
    (throw (ex-info "TYPESAFE_API_KEY is not set." {}))))

(defn questions
  [{:keys [state questions]}]

  (require-api-key)

  (let [request-headers {"content-type" "application/json"
                         "authorization" (str "Bearer " api-key)}
        response
        (try
          (hc/post url
                   {:headers request-headers
                    :body (api/encode
                           {:model model
                            :state state
                            :questions questions})
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "Typesafe request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body headers status]}
        response]
    (when-not (<= 200 status 299)
      (api/throw-response-error response request-headers status-message))
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
