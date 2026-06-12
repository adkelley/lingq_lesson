#!/usr/bin/env bb

(require
 '[clojure.java.io :as io]
 '[clojure.string :as string]
 '[hato.client :as hc]
 '[cheshire.core :as cheshire]
 '[babashka.cli :as cli]
 '[babashka.fs :as fs])

(import
 '[java.nio.charset StandardCharsets])

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

(defn- openai-key [] (System/getenv "OPENAI_API_KEY"))
(def openai-speech-url "https://api.openai.com/v1/audio/speech")

(def voices #{"alloy" "ash" "ballad" "coral" "echo"
              "fable" "nova" "onyx" "sage" "shimmer" "verse"})

(def supported-audio-formats #{"mp3" "wav"})

(def default-tts-opts {:model "gpt-4o-mini-tts"
                       :voice "alloy"
                       :format "mp3"
                       :instructions "Read like a calm news narrator"})

(defn- require-api-key []
  (when-not (seq (openai-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- request-headers [content-type]
  (cond-> {"authorization" (format "Bearer %s" (openai-key))}
    content-type (assoc "content-type" content-type)))

(defn- write-bytes! [file body]
  (when-let [parent (fs/parent file)]
    (fs/create-dirs parent))
  (let [bytes (cond
                (instance? (Class/forName "[B") body) body
                (string? body) (.getBytes ^String body StandardCharsets/UTF_8)
                :else nil)]
    (when-not (and bytes (pos? (alength ^bytes bytes)))
      (throw (ex-info "OpenAI speech response body was empty."
                      {})))
    (try
      (with-open [out (io/output-stream file)]
        (.write out ^bytes bytes))
      (.getPath file)
      (catch Exception e
        (throw (ex-info "Speech output write failed."
                        {:output-path (.getPath file)
                         :error (.getMessage e)}
                        e))))))

(defn- output-file [output-path format]
  (if (seq output-path)
    (io/file output-path)
    (java.io.File/createTempFile "openai-speech-" (str "." format) (io/file "/tmp"))))

(defn text-to-speech [text opts]
  (require-api-key)
  (let [request-opts (merge default-tts-opts opts)
        payload (cond-> {:model (:model request-opts)
                         :input text
                         :voice (:voice request-opts)
                         :response_format (:format request-opts)}
                  (:instructions request-opts)
                  (assoc :instructions (:instructions request-opts)))
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
    (write-bytes! (output-file (:output request-opts)
                               (:format request-opts))
                  body)))

;; CLI
(defn- valid-voice?
  [voice]
  (contains? voices voice))

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn- valid-audio-format? [path]
  (contains? supported-audio-formats
             (string/lower-case (str (fs/extension path)))))

(def cli-spec
  {:restrict false
   :spec
   {:output       {:alias :o
                   :desc "Output audio file path"
                   :validate valid-audio-format?
                   :default "article.mp3"}
    :voice        {:alias :v
                   :default "alloy"
                   :validate valid-voice?
                   :desc "Voice to use"}
    :instructions {:alias :i
                   :desc "Voice/style instructions"
                   :default (:instructions default-tts-opts)}
    :help         {:alias :h
                   :coerce :boolean
                   :desc "Show help"}}

   :error-fn
   (fn [{:keys [spec type cause msg option] :as data}]
     (binding [*out* *err*]
       (when (= :org.babashka/cli type)
         (case cause
           :require
           (println
            (format "Missing required argument: %s - %s"
                    option
                    (:desc spec)))

           :validate
           (println msg)

           (do
             (println "Unexpected error:")
             (prn data)))))
     (System/exit 1))})

(defn- log-info [message]
  (binding [*out* *err*]
    (println message)
    (flush)))

(defn- read-input-text [input-file]
  (if input-file
    (slurp input-file)
    (slurp *in*)))

(defn- output-format [output-path]
  (string/lower-case (str (fs/extension output-path))))

(defn- cli-opts->tts-opts [opts]
  (merge
   (select-keys opts [:voice :instructions :output])
   {:format (output-format (:output opts))}))

(def usage
  "Usage: text_to_speech.bb [path_to_text_file] --output <audio_file> [options]")

(defn- help?
  [args]
  (some #{"--help" "-h"} args))

(defn- show-help
  [spec]
  (str usage
       "\n\n"
       "Reads article text from a file argument, or from stdin when no file is provided.\n"
       "Generates speech audio using OpenAI text-to-speech.\n\n"
       "Options:\n"
       (cli/format-opts (merge spec {:order [:output :voice :instructions :help]}))
       "\n"
       "Environment:\n"
       "  OPENAI_API_KEY               Required.\n\n"
       "Examples:\n"
       "  text_to_speech.bb article.txt --output article.mp3\n"
       "  cat article.txt | text_to_speech.bb --output article.wav --voice nova\n"
       "  text_to_speech.bb article.txt -o article.wav -v alloy"))

(when (help? *command-line-args*)
  (println (show-help cli-spec))
  (System/exit 0))

(defn- error-message [e]
  (if (instance? clojure.lang.ExceptionInfo e)
    (.getMessage e)
    (str "TTS conversion failed: " (.getMessage e))))

(let [{:keys [args opts]} (cli/parse-args *command-line-args* cli-spec)
      input-file (first args)]
  (when (> (count args) 1)
    (exit-with-error usage))

  (let [text (read-input-text input-file)
        tts-opts (cli-opts->tts-opts opts)]
    (when (string/blank? text)
      (exit-with-error "Input text is empty."))

    (log-info (format "Starting TTS conversion: %s"
                      (or input-file "stdin")))
    (try
      (text-to-speech text tts-opts)
      (catch Exception e
        (exit-with-error (error-message e)))

      (log-info (format "TTS audio saved to: %s" (:output tts-opts))))))
