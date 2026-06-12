#!/usr/bin/env bb

(require
 '[babashka.cli :as cli]
 '[babashka.fs :as fs]
 '[cheshire.core :as json]
 '[clojure.java.io :as io]
 '[clojure.string :as str]
 '[hato.client :as hc])
(import
 '[java.nio.charset StandardCharsets])

(def ^:private lingq-api-key (System/getenv "LINGQ_API_KEY"))

(defn parse-json-body [body]
  (when (seq body)
    (json/decode body keyword)))

(defn encode [value]
  (json/encode value))

(defn redact-headers [headers]
  (cond-> headers
    (and (map? headers) (contains? headers "authorization"))
    (assoc "authorization" "[REDACTED]")))

(defn status-message [status]
  (case status
    429 "LingQ returned status 429 (rate limited). Slow down requests or retry after the Retry-After interval."
    401 "LingQ returned status 401. Check that LINGQ_API_KEY is set and valid."
    403 "LingQ returned status 403. Your API key does not have access to this resource."
    (str "LingQ returned status " status ".")))

(defn throw-response-error [response request-headers]
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

(defn- body->str [body]
  (cond
    (string? body) body
    (instance? (Class/forName "[B") body)
    (String. body StandardCharsets/UTF_8)
    :else
    (some-> body str)))

(defn- request-headers []
  {"authorization" (format "Token %s" lingq-api-key)
   "content-type" "application/json"})

(def ^:private max-retries 3)
(def ^:private default-retry-after-seconds 30)

(defn- retry-after-seconds
  "Reads the Retry-After response header (seconds form), falling back to a
  default when it is absent or not an integer."
  [response]
  (or (some-> (get-in response [:headers "retry-after"]) str/trim parse-long)
      default-retry-after-seconds))

(defn- post-with-retry
  "POSTs to url, retrying on HTTP 429 up to max-retries, sleeping for the
  Retry-After interval between attempts. hato rebuilds the multipart body on
  each call, so the file/text parts are re-sent intact."
  [url opts]
  (loop [attempt 1]
    (let [response (try
                     (hc/post url opts)
                     (catch Exception e
                       (throw (ex-info "Failed to post import request" {:url url} e))))]
      (if (and (= 429 (:status response)) (< attempt max-retries))
        (let [secs (retry-after-seconds response)]
          (println (format "Rate limited (429); retrying in %ds (attempt %d/%d)"
                           secs attempt max-retries))
          (Thread/sleep (* secs 1000))
          (recur (inc attempt)))
        response))))

(defn- multipart-parts
  "Builds the import POST body. `tags` and `description` are sent here (verified
  to persist on import — re-GET to see them, the 201 echo omits tags), so the
  only field left for the follow-up PATCH is `audio`."
  [title srt-file status {:keys [image-file level tags description]}]
  (cond-> [{:name "title" :content title}
           {:name "file" :content (io/file srt-file)}
           {:name "filename" :content srt-file}
           {:name "status" :content status}
           {:name "save" :content "true"}]
    image-file        (conj {:name "image" :content (io/file image-file)})
    level             (conj {:name "level" :content (str level)})
    (seq tags)        (conj {:name "tags" :content (str/join "," tags)})
    (seq description) (conj {:name "description" :content description})))

(defn- post-import-request [multipart]
  (when-not (seq lingq-api-key)
    (throw (ex-info "LINGQ_API_KEY not set" {})))
  ;; v3 import endpoint (v2 /lessons/ is obsolete). We import the .srt as a FILE
  ;; (the web "Text Files & Ebooks" path / importEbook() builder), NOT as `text`:
  ;;   title, file, filename, status, save=true  (multipart/form-data)
  ;; Uploaded as a file, LingQ parses the SubRip timestamps for audio sync — which
  ;; sending the .srt as plain `text` does not do. save=true commits the lesson
  ;; (201); without it the endpoint only parses and returns 200. hato fills the
  ;; file part's filename from the File's name. Audio/tags/description are attached
  ;; afterward via PATCH (see -main) once tokenization unlocks the lesson.
  (let [url "https://www.lingq.com/api/v3/ja/lessons/import/"
        response (post-with-retry url {:headers (request-headers)
                                       :multipart multipart
                                       :throw-exceptions false})
        {:keys [body status]} response
        response-body (body->str body)
        parsed-body (parse-json-body response-body)]
    (println "HTTP status:" status)
    (println "Location:" (get-in response [:headers "location"]))
    (println "lesson id:" (or (:id parsed-body) (:pk parsed-body) "<none in body>"))
    (when-not (<= 200 status 299)
      (throw-response-error (assoc response :body response-body) (request-headers)))
    (when-not parsed-body
      (throw (ex-info "No body in response" {:status status :body response-body})))
    parsed-body))

(defn- patch-lesson
  "Attaches audio to an existing lesson, mirroring the web app's updateLesson():
  PATCH /lessons/{id}/ with an `audio` file part. Audio is transcoded async
  (watch audioPending). Audio still needs the post-lock timing (a PATCH during
  the tokenization lock returns 200 but drops the audio); tags/description are
  set at import time instead (see multipart-parts)."
  [lesson-id audio-file]
  (let [url (format "https://www.lingq.com/api/v3/ja/lessons/%s/" lesson-id)
        multipart [{:name "audio" :content (io/file audio-file)}]
        response (hc/patch url {:headers (request-headers)
                                :multipart multipart
                                :throw-exceptions false})
        {:keys [body status]} response
        response-body (body->str body)
        parsed-body (parse-json-body response-body)]
    (println "PATCH status:" status)
    (when-not (<= 200 status 299)
      (throw-response-error (assoc response :body response-body) (request-headers)))
    parsed-body))

(defn- get-lesson
  "Fetches a lesson by id so we can verify what actually stuck server-side.
  Right after import LingQ locks the lesson while it tokenizes the text
  (400 {:errorType \"locked\" :isLocked \"TOKENIZE_TEXT\"}); we poll past that."
  [lesson-id]
  (let [url (format "https://www.lingq.com/api/v3/ja/lessons/%s/" lesson-id)]
    (loop [attempt 1]
      (let [response (hc/get url {:headers (request-headers) :throw-exceptions false})
            {:keys [body status]} response
            response-body (body->str body)
            parsed-body (parse-json-body response-body)]
        (cond
          (<= 200 status 299)
          parsed-body

          (and (= 400 status) (= "locked" (:errorType parsed-body)) (< attempt 6))
          (do (println (format "  lesson locked (%s); retrying in 3s (%d/6)"
                               (:isLocked parsed-body) attempt))
              (Thread/sleep 3000)
              (recur (inc attempt)))

          :else
          (throw-response-error (assoc response :body response-body) (request-headers)))))))

;; CLI

(defn show-help
  [spec]
  (cli/format-opts (merge spec {:order (sort (vec (keys (:spec spec))))})))

(defn srt-file-exists?
  [path]
  (let [ext (str/lower-case (str (fs/extension path)))]
    (and (fs/exists? path) (= ext "srt"))))

(defn- audio-file-exists?
  [path]
  (let [supported-formats #{"mp3" "wav"}
        ext (str/lower-case (str (fs/extension path)))]
    (and (supported-formats ext) (fs/exists? path))))

(def ^:private max-image-bytes (* 200 1024 1024)) ; LingQ caps cover images at 200 MB

(defn- image-file-error
  "Returns a human-readable reason the image at `path` is unusable, or nil if it
  is a valid cover image (supported format, exists, within the size cap)."
  [path]
  (let [supported-formats #{"jpg" "jpeg" "png" "gif"}
        ext (str/lower-case (str (fs/extension path)))]
    (cond
      (not (supported-formats ext))
      (format "Unsupported image format '.%s'; use jpg, jpeg, png or gif." ext)

      (not (fs/exists? path))
      (format "Image file not found: %s" path)

      (> (fs/size path) max-image-bytes)
      (format "Image is %.1f MB, exceeding the 200 MB limit."
              (/ (fs/size path) 1024.0 1024.0)))))

(def cli-spec
  {:spec
   {:srt-file {:desc "SRT file path"
               :alias :s
               :require true
               :validate srt-file-exists?}
    :audio-file {:desc "Audio file path (mp3 or wav)"
                 :alias :a
                 :validate audio-file-exists?
                 :require true}
    :title {:desc "Lesson title"
            :alias :t
            :require true}
    :description {:desc "Lesson description"
                  :alias :d
                  :default ""}
    :tags {:desc "Lesson tags"
           :coerce (fn [tags] (if (string? tags) (clojure.string/split tags #",") tags))
           :default []}
    :image-file {:desc "Cover image path (jpg, jpeg, png or gif, max 200 MB); required when --status shared"
                 :alias :i}
    :level {:desc "Lesson level 0-6 (0 No Knowledge, 1-2 Beginner, 3-4 Intermediate, 5-6 Advanced); shared lessons need >0"
            :alias :l
            :coerce :long
            :default 0
            :validate #{0 1 2 3 4 5 6}}
    :status {:desc "Lesson status (private or shared)"
             :default "private"
             :validate #{"private" "shared"}}
    :work-dir {:desc "Working directory for files"
               :alias :w
               :default "."
               :validate fs/directory?}}
   :error-fn                           ; a function to handle errors
   (fn [{:keys [spec type cause msg option] :as data}]
     (when (= :org.babashka/cli type)
       ;; babashka.cli hands us the whole spec map, so the option's :desc is at
       ;; (get-in spec [option :desc]), not (:desc spec).
       (let [desc (get-in spec [option :desc])]
         (case cause
           :require
           (println
            (format "Missing required argument: %s - %s\n"
                    option
                    desc))
           :validate
           (println (format "%s - %s\n"
                            msg
                            desc))
           (do
             (println "Unexpected error:")
             (prn data))))
       (throw (ex-info "Invalid command line arguments" data))))})

(defn- help?
  [args]
  (some #{"--help" "-h"} args))

(when (help? *command-line-args*)
  (println (show-help cli-spec))
  (System/exit 0))

(let [opts (cli/parse-opts *command-line-args* cli-spec)
      work-dir (:work-dir opts)
      image-file (format "%s/%s" work-dir (:image-file opts))
      _ (when (and (= "shared" (:status opts)) (not image-file))
          (println "A cover image is required to share a lesson; pass --image <jpg|jpeg|png|gif>.")
          (System/exit 1))
      _ (when-let [err (and image-file (image-file-error image-file))]
          (println err)
          (System/exit 1))
      srt-file (format "%s/%s" work-dir (:srt-file opts))
      multipart (multipart-parts (:title opts) srt-file (:status opts)
                                 {:image-file image-file
                                  :level (:level opts)
                                  :tags (:tags opts)
                                  :description (:description opts)})
      lesson (post-import-request multipart)
      id (:id lesson)]
  (if id
    (do
      (println "Lesson" id (:lessonURL lesson))
          ;; Tags/description were set at import. Only audio remains, and a PATCH on a
          ;; locked (tokenizing) lesson returns 200 but drops the audio — so wait for
          ;; the lock to clear first; get-lesson polls past it.
      (println "Waiting for tokenization to finish…")
      (get-lesson id)
      (patch-lesson id (format "%s/%s" work-dir (:audio-file opts)))
          ;; Re-fetch to confirm what stuck. Best-effort: a verify hiccup must not
          ;; fail the run. Audio transcodes async, so audioPending may be true right
          ;; after — duration matching the mp3 length means the file was accepted.
      (try
        (let [saved (get-lesson id)]
          (println "  audioUrl:    " (:audioUrl saved))
          (println "  audioPending:" (:audioPending saved))
          (println "  duration:    " (:duration saved))
          (println "  wordCount:   " (:wordCount saved))
          (println "  tags:        " (:tags saved))
          (println "  description: " (:description saved)))
        (catch Exception e
          (println "  (verify skipped:" (ex-message e) "— open the URL above)"))))
    (println "No lesson id in import response; skipping audio/tags attach."))
  (System/exit 0))
