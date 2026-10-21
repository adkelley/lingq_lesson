(ns lingq-lesson.audio
  (:require
   [lingq-lesson.openai :as openai]
   [hato.client :as hc]
   [lingq-lesson.api :as api]
   [lingq-lesson.audio-instructions :as audio-instructions]))

(def voices #{"alloy" "echo" "nova" "onyx" "shimmer"})

(defn supported-voice?
  [voice]
  (contains? voices voice))

(def supported-audio-formats #{"mp3" "wav"})

(def ^:private model "gpt-4o-mini-tts")

(def default-tts-opts {:model model
                       :voice "alloy"
                       :format "mp3"
                       :vibe "news"})

(defn text-to-speech! [text opts]
  (openai/require-api-key)
  (let [request-opts (merge default-tts-opts opts)
        instructions (audio-instructions/for-vibe (:vibe request-opts))
        payload (cond-> {:model (:model request-opts)
                         :input text
                         :voice (:voice request-opts)
                         :response_format (:format request-opts)}
                  (seq instructions)
                  (assoc :instructions instructions))
        headers (api/request-headers (openai/api-key) "application/json")
        response
        (try
          (hc/post openai/speech-url
                   {:headers headers
                    :body (api/encode payload)
                    :as :byte-array
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI audio speech request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]} response]
    (when-not (<= 200 status 299)
      (api/throw-response-error (assoc response :body (api/body->string body))
                                   headers openai/status-message))
    body))
