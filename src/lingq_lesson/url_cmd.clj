(ns lingq-lesson.url-cmd
  (:require
   [clojure.string :as str]
   [lingq-lesson.openai.text-to-speech :as tts]
   [lingq-lesson.audio-instructions :as audio-instructions]
   [lingq-lesson.lingq :as lingq]
   [lingq-lesson.parser :as parser]
   [lingq-lesson.typesafe.article-classifier :as typesafe-article-classifier]
   [lingq-lesson.openai.article-classifier :as openai-article-classifier]))

(defn- positive-course-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(defn- supported-values-desc
  [values]
  (str "(" (str/join ", " (sort values)) ")"))

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

(defn- valid-url?
  [value]
  (try
    (validate-url! value)
    true
    (catch clojure.lang.ExceptionInfo _
      false)))

(def choice->difficulty
  {"N5"       {:jlpt-level "N5" :lingq-level 1}
   "N4"       {:jlpt-level "N4" :lingq-level 2}
   "N3"       {:jlpt-level "N3" :lingq-level 3}
   "N2"       {:jlpt-level "N2" :lingq-level 4}
   "N1"       {:jlpt-level "N1" :lingq-level 5}
   "above-n1" {:jlpt-level "Above N1" :lingq-level 6}})

(def supported-classifiers #{"typesafe" "openai"})

(def url-spec
  ;; TODO: Add a public/private option
  {:url          {:desc "URL of the article to use"
                  :require true
                  :validate valid-url?}
   :voice        {:desc (str "Voice to use " (supported-values-desc tts/voices))
                  :require false
                  :validate tts/supported-voice?}
   :category        {:desc (str "Article subject category " (supported-values-desc audio-instructions/supported-categories))
                     :require false
                     :validate audio-instructions/supported-category?}
   :image        {:desc "Cover image to use"
                  :require false
                  :validate valid-url?
                  :default nil}
   :course-id    {:desc "LingQ course ID to assign the lesson to"
                  :require false
                  :default nil
                  :validate positive-course-id?}
   :jlpt-level  {:desc "JLPT level (N5-N1, above N1)"
                 :require false
                 :default nil
                 :coerce str/upper-case
                 :validate #(contains? choice->difficulty %)}
   :dry-run      {:desc "Assess article category and difficulty without creating a lesson"
                  :require false
                  :default false
                  :validate boolean?}
   :classifier  {:desc "Article classifier to use (typesafe, openai)"
                 :require false
                 :default "openai"
                 :validate #(contains? supported-classifiers %)}})

(def url-doc
  "Create a private Japanese LingQ lesson from an article URL. Other languages are not supported. Category determines narration instructions and the default voice.")

(def ^:private category->voice
  {"society" "alloy"
   "politics" "onyx"
   "business" "onyx"
   "sports" "echo"
   "international" "alloy"
   "regional" "alloy"
   "science-technology" "alloy"
   "culture" "nova"
   "lifestyle" "nova"
   "education-careers" "alloy"
   "health" "alloy"
   "weather" "alloy"
   "other" "alloy"})

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
  [category]
  (get category->voice category "alloy"))

(defn- create-audio!
  [text title category voice-override]
  (let [voice (or voice-override (resolve-voice category))]
    (println-status
     (str "Creating audio for " title " with category: " category " and voice: " voice))
    (tts/text-to-speech! (str title "\n\n" text)
                           {:voice voice
                            :category category})))

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
  (println "Uploading lesson to Lingq:")
  (println (format "  title: %s\n  url:   %s\n" title original-url)))

(defn- print-assessment!
  [article category difficulty]
  (println (format "Title: %s" (:title article)))
  (println (format "Category: %s" category))
  (println (format "Lingq Level: %s" (:lingq-level difficulty)))
  (println (format "JLPT Level: %s" (:jlpt-level difficulty))))

(defn- create-lesson!
  [opts]
  (let [source-url (:url opts)
        image-url (:image opts)
        course-id (:course-id opts)
        article (parser/parse-article source-url image-url
                                      {:download-image? (not (:dry-run opts))})
        text (:text article)
        title (:title article)
        classification (when-not (and (:category opts) (:jlpt-level opts))
                         (case (:classifier opts)
                           "typesafe" (typesafe-article-classifier/article-text->classification! text)
                           "openai" (openai-article-classifier/article-text->classification! text)))
        difficulty (or (choice->difficulty (:jlpt-level opts)) (choice->difficulty (:jlpt-level classification)))
        category (or (:category opts) (:category classification))]
    (print-assessment! article category difficulty)
    (when-not (:dry-run opts)
      (let [audio (create-audio! text title category (:voice opts))
            lesson (build-lesson article
                                 source-url
                                 audio
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
