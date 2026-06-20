(ns lingq-lesson.audio
  (:require
   [lingq-lesson.openai :as openai]
   [hato.client :as hc]))

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

(defn- vibe->instructions [vibe]
  (case vibe
    "newscaster" newscaster-instructions
    "default" newscaster-instructions))

(defn text-to-speech! [text opts]
  (openai/require-api-key)
  (let [request-opts (merge default-tts-opts opts)
        instructions (vibe->instructions (:vibe request-opts))
        payload (cond-> {:model (:model request-opts)
                         :input text
                         :voice (:voice request-opts)
                         :response_format (:format request-opts)}
                  (seq instructions)
                  (assoc :instructions instructions))
        headers (openai/request-headers "application/json")
        response
        (try
          (hc/post openai/speech-url
                   {:headers headers
                    :body (openai/encode payload)
                    :as :byte-array
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI audio speech request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]} response]
    (when-not (<= 200 status 299)
      (openai/throw-response-error (assoc response :body (openai/body->string body))
                                   headers))
    body))
