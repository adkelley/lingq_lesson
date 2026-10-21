(ns lingq-lesson.url-cmd
  (:require
   [clojure.string :as str]
   [lingq-lesson.audio :as audio]
   [lingq-lesson.audio-instructions :as audio-instructions]
   [lingq-lesson.jlpt-level :as jlpt-level]
   [lingq-lesson.lingq :as lingq]
   [lingq-lesson.parser :as parser]
   [lingq-lesson.style-classifier :as style-classifier]))

(defn- positive-course-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(defn- supported-values-desc
  [values]
  (str "(" (str/join ", " (sort values)) ")"))

(def url-spec
  {:voice        {:desc (str "Voice to use " (supported-values-desc audio/voices))
                  :require false
                  :validate audio/supported-voice?}
   :vibe         {:desc (str "Voice/style instructions " (supported-values-desc audio-instructions/supported-vibes))
                  :require false
                  :validate audio-instructions/supported-vibe?}
   :silent       {:desc "Silent mode"
                  :require false
                  :default false
                  :validate boolean?}
   :image        {:desc "images to use"
                  :require false
                  :default nil}
   :course-id    {:desc "LingQ course ID to assign the lesson to"
                  :require false
                  :default nil
                  :validate positive-course-id?}
   :lingq-level  {:desc "LingQ lesson level (1-6)"
                  :require false
                  :coerce :long
                  :validate #(<= 1 % 6)}
   :dry-run      {:desc "Assess article style and difficulty without creating a lesson"
                  :require false
                  :default false
                  :validate boolean?}})

(def url-doc
  (str
   "Create a LingQ lesson from an article URL.\n"
   "\nRecommended Vibe -> Voice: \n"
   "- business   -> onyx\n"
   "- crime      -> onyx\n"
   "- entertainment -> nova\n"
   "- news       -> alloy\n"
   "- politics   -> onyx\n"
   "- sports     -> echo\n"
   "- lifestyle  -> nova\n"
   "- technology -> alloy\n"))

(def ^:private vibe->voice
  {"business" "onyx"
   "crime" "onyx"
   "entertainment" "nova"
   "lifestyle" "nova"
   "news" "alloy"
   "politics" "onyx"
   "sports" "echo"
   "technology" "alloy"})

(def ^:private status-output-lock (Object.))

(defn- println-status
  [msg]
  (locking status-output-lock
    (println msg)
    (flush)))

(defn- fail!
  [msg]
  (binding [*out* *err*]
    (println (str "Error: " msg)))
  (System/exit 1))

(defn- resolve-voice
  [vibe]
  (get vibe->voice vibe "alloy"))

(defn- resolve-vibe!
  [article-text verbose]
  (:vibe (style-classifier/article-text->style! article-text verbose)))

(defn- resolve-style-opts!
  [text opts verbose]
  (when (and verbose (nil? (:vibe opts)))
    (println-status "Assessing article vibe and style"))
  (let [vibe (or (:vibe opts) (resolve-vibe! text verbose))
        voice (or (:voice opts) (resolve-voice vibe))]
    {:vibe vibe :voice voice}))

(defn- create-audio!
  [{:keys [text title]} opts verbose]
  (let [{:keys [vibe voice]} (resolve-style-opts! text opts verbose)]
    (println-status
     (str "Creating audio for " title " with style: " vibe " and voice: " voice))
    (audio/text-to-speech! (str title "\n\n" text)
                           {:voice voice
                            :vibe vibe})))

(defn- assess-difficulty!
  [{:keys [text title]} verbose]
  (println-status (str "Assessing difficulty for " title))
  (jlpt-level/article-text->jlpt-level! text verbose))

(defn- build-lesson
  [article source-url audio-result difficulty course-id]
  (merge article
         {:status "private"
          :level (:lingq-level difficulty)
          :audio audio-result
          :original-url (or (:original-url article) source-url)
          :course-id course-id}))

(defn- print-lesson-summary!
  [{:keys [title original-url]}]
  (println "Creating lesson:")
  (println (format "  title: %s\n  url:   %s\n" title original-url)))

(defn- validate-url!
  [url]
  (try
    (let [uri (java.net.URI. url)]
      (when-not (and (#{"http" "https"} (.getScheme uri))
                     (some? (.getHost uri)))
        (throw (ex-info "Invalid URL" {:url url})))
      url)
    (catch IllegalArgumentException cause
      (throw (ex-info "Invalid URL" {:url url} cause)))))

(defn- resolve-difficulty!
  [article opts verbose]
  (if-some [lingq-level (:lingq-level opts)]
    {:lingq-level lingq-level}
    (assess-difficulty! article verbose)))

(defn- dry-run!
  [article opts verbose]
  (let [style (resolve-style-opts! (:text article) opts verbose)
        difficulty (resolve-difficulty! article opts verbose)]
    (println "\nDry run complete")
    (println (format "Title: %s" (:title article)))
    (println (format "Style: %s" (:vibe style)))
    (println (format "Lingq Level: %s" (:lingq-level difficulty)))
    (when-some [level (:jlpt-level difficulty)]
      (println (format "JLPT Level: %s" level)))))

(defn- create-lesson!
  [opts]
  (let [source-url (validate-url! (:url opts))
        image-url (if (:image opts) (validate-url! (:image opts)) nil)
        course-id (:course-id opts)
        article (parser/parse-article source-url image-url
                                      {:download-image? (not (:dry-run opts))})
        verbose (not (:silent opts))]
    (if (:dry-run opts)
      (dry-run! article opts verbose)
      (let [audio-future (future (create-audio! article opts verbose))
            difficulty (resolve-difficulty! article opts verbose)
            lesson (build-lesson article
                                 source-url
                                 @audio-future
                                 difficulty
                                 course-id)]
        (print-lesson-summary! lesson)
        (lingq/create-lesson lesson)))))

(defn url
  [{:keys [opts]}]
  (try
    (create-lesson! opts)
    (catch clojure.lang.ExceptionInfo e
      (fail! (str (ex-message e) " " (pr-str (ex-data e)))))))
