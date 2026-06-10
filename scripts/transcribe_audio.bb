#!/usr/bin/env bb

(require
 '[clojure.java.io :as io]
 '[clojure.string :as string]
 '[hato.client :as hc]
 '[cheshire.core :as cheshire]
 '[babashka.cli :as cli]
 '[babashka.fs :as fs])

(import
 '[java.net URI URLDecoder]
 '[java.nio.charset StandardCharsets])

;; TTS
(defn- openai-key [] (System/getenv "OPENAI_API_KEY"))

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

(defn- auth-headers []
  {"authorization" (format "Bearer %s" (openai-key))})

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

(def openai-speech-url "https://api.openai.com/v1/audio/speech")

(def supported-audio-formats #{"mp3" "wav"})

(defn- require-api-key []
  (when-not (seq (openai-key))
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- url-string? [value]
  (and (string? value)
       (re-matches #"https?://.+" value)))

(defn- require-local-file [file-path]
  (let [file (io/file file-path)]
    (when-not (.exists file)
      (throw (ex-info "File does not exist."
                      {:file-path file-path})))
    file))

(defn- decode-url-segment [segment]
  (URLDecoder/decode segment (.name StandardCharsets/UTF_8)))

(defn- filename-from-url [url]
  (let [path (.getPath (URI. url))
        segment (some-> path (string/split #"/") last decode-url-segment)]
    (when (seq segment)
      segment)))

(defn- download-url-to-temp-file [url]
  (let [filename (filename-from-url url)
        temp-file (java.io.File/createTempFile "openai-audio-" (fs/extension filename))]
    (try
      (let [{:keys [body status]} (hc/get url {:as :stream
                                               :throw-exceptions false})]
        (when-not (<= 200 status 299)
          (throw (ex-info "Audio download returned a non-success status."
                          {:status status
                           :url url})))
        (with-open [in body]
          (io/copy in temp-file)))
      temp-file
      (catch Exception e
        (throw (ex-info "Audio download failed."
                        {:url url
                         :error (.getMessage e)}
                        e))))))

(defn- audio-source->file [audio-source]
  (cond
    (not (string? audio-source))
    (throw (ex-info "Audio source must be a string."
                    {:audio-source audio-source}))

    (url-string? audio-source)
    {:file (download-url-to-temp-file audio-source)
     :delete-after? true}

    :else
    {:file (require-local-file audio-source)
     :delete-after? false}))

(defn- request-raw-multipart [url multipart failure-message]
  (require-api-key)
  (let [headers (auth-headers)
        response
        (try
          (hc/post url {:headers headers
                        :multipart multipart
                        :throw-exceptions false})
          (catch Exception e
            (throw (ex-info failure-message
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]} response
        response-body (body->string body)]
    (when-not (<= 200 status 299)
      (throw-response-error (assoc response :body response-body)
                            headers))
    (when-not (seq response-body)
      (throw (ex-info (str failure-message " response body was empty.")
                      {:status status
                       :body response-body
                       :response {:status status
                                  :body response-body
                                  :opts {:headers (redact-headers headers)}}})))
    response-body))

(defn- multipart-value [value]
  (when (some? value)
    (str value)))

(defn- request-audio-srt [audio-source opts]
  (let [{:keys [file delete-after?]} (audio-source->file audio-source)
        request-opts (merge {:model "whisper-1"
                             :language "en"
                             :response-format "srt"}
                            opts)
        multipart (->> [[:file file]
                        [:model (:model request-opts)]
                        [:prompt (:prompt request-opts)]
                        [:language (:language request-opts)]
                        [:temperature (:temperature request-opts)]
                        [:response_format (:response-format request-opts)]]
                       (keep (fn [[field value]]
                               (when (some? value)
                                 {:name (name field)
                                  :content (if (= :file field)
                                             value
                                             (multipart-value value))})))
                       vec)]
    (try
      (request-raw-multipart "https://api.openai.com/v1/audio/transcriptions"
                             multipart
                             "OpenAI audio transcription request failed.")
      (finally
        (when delete-after?
          (.delete file))))))

(defn- srt-output-file [audio-source output-path]
  (io/file (or output-path
               (str (fs/strip-ext audio-source) ".srt"))))

(defn- write-text! [file text failure-message]
  (when-not (seq text)
    (throw (ex-info "OpenAI text response body was empty." {})))
  (try
    (spit file text)
    (.getPath file)
    (catch Exception e
      (throw (ex-info failure-message
                      {:output-path (.getPath file)
                       :error (.getMessage e)}
                      e)))))

(defn transcribe-audio-srt
  ([audio-source]
   (transcribe-audio-srt audio-source {}))
  ([audio-source opts-or-output-path]
   (let [opts (if (map? opts-or-output-path)
                opts-or-output-path
                {:output-path opts-or-output-path})
         result (request-audio-srt audio-source opts)
         file (srt-output-file audio-source (:output-path opts))]
     (write-text! file result "SRT output write failed.")))
  ([audio-source output-path opts]
   (when-not (map? opts)
     (throw (ex-info "Options must be a map." {})))
   (transcribe-audio-srt audio-source (assoc opts :output-path output-path))))

;; CLI
(defn- valid-audio-format? [path]
  (contains? supported-audio-formats
             (string/lower-case (str (fs/extension path)))))

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(def usage
  "Usage: transcribe_audio.bb <path_to_audio_file> [--output <path_to_srt_file>]")

(defn help-text []
  (str usage
       "\n\n"
       "Transcribes audio from a file argument.\n"
       "Writes transcription to a SubRip subtitle (.srt) file.\n"
       "\n"
       "Environment:\n"
       "  OPENAI_API_KEY               Required.\n\n"
       "Options:\n"
       "  -o, --output <file>          Optional. Output .srt file path.\n"
       "                               Defaults to input file with .srt extension.\n"
       "  -h, --help                   Show this help message.\n"
       "\n"
       "Examples:\n"
       "  transcribe_audio.bb article.mp3\n"
       "  transcribe_audio.bb article.mp3 --output article.srt\n"))

(defn print-help []
  (println (help-text)))

(def cli-spec
  {:restrict false
   :spec
   {:output {:alias :o
             :desc "Output .srt file path"}
    :language {:alias :l
               :desc "Language code (default: ja)"}
    :help   {:alias :h
             :coerce :boolean
             :desc "Show help"}}
   :error-fn
   (fn [{:keys [type cause msg] :as data}]
     (binding [*out* *err*]
       (when (= :org.babashka/cli type)
         (case cause
           :validate
           (println msg)

           (do
             (println "Unexpected error:")
             (prn data)))))
     (System/exit 1))})

(defn valid-srt-path? [path]
  (= "srt" (string/lower-case (str (fs/extension path)))))

(let [{:keys [args opts]} (cli/parse-args *command-line-args* cli-spec)
      audio (first args)
      output-path (:output opts)
      language (or (:language opts) "ja")]

  (when (:help opts)
    (print-help)
    (System/exit 0))

  (when (> (count args) 1)
    (exit-with-error usage))

  (when-not (and audio (valid-audio-format? audio))
    (exit-with-error "Input file must be an mp3 or wav audio file"))

  (when (and output-path (not (valid-srt-path? output-path)))
    (exit-with-error "Output file must be a .srt file"))

  (transcribe-audio-srt audio {:output-path output-path :language language}))
