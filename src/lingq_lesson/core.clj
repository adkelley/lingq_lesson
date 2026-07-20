(ns lingq-lesson.core
  (:require
   [clojure.string :as str]
   [babashka.deps :as deps]
   [lingq-lesson.audio :as audio]
   [lingq-lesson.audio-instructions :as audio-instructions]
   [lingq-lesson.lingq :as lingq]
   [lingq-lesson.style-classifier :as style-classifier]
   [lingq-lesson.jlpt-level :as jlpt-level]
   [lingq-lesson.parser :as parser]))

;; bb currently bundles an older babashka.cli; add and reload the newer
;; version so automatic help/completions are available before dispatch.
;; Remove this once Babashka ships with babashka.cli 0.12.75 or newer.
(deps/add-deps '{:deps {org.babashka/cli {:mvn/version "0.12.75"}}})
(require '[babashka.cli :as cli] :reload)

(def app-doc
  (str
   "Create a LingQ lesson from an article URL.\n"
   "\nRecommended Vibe -> Voice: \n"
   "- business   -> onyx\n"
   "- crime      -> onyx\n"
   "- entertainment -> nova\n"
   "- news       -> alloy\n"
   "- sports     -> echo\n"
   "- lifestyle  -> nova\n"
   "- technology -> alloy\n"))

(defn valid-url?
  [s]
  (try
    (let [uri (java.net.URI. s)]
      (and (#{"http" "https"} (.getScheme uri))
           (some? (.getHost uri))))
    (catch Exception _
      false)))

(defn supported-values-desc
  [values]
  (str "(" (str/join ", " (sort values)) ")"))

(def spec
  {:url          {:desc "URL article"
                  :require true
                  :validate {:pred valid-url?
                             :ex-msg (fn [{:keys [value]}]
                                       (str "Invalid URL: " value))}}
   :voice        {:desc (str "Voice to use " (supported-values-desc audio/voices))
                  :require false
                  :validate audio/supported-voice?}
   :vibe         {:desc (str "Voice/style instructions " (supported-values-desc audio-instructions/supported-vibes))
                  :require false
                  :validate audio-instructions/supported-vibe?}})

(defn- fail!
  [msg]
  (binding [*out* *err*]
    (println (str "Error: " msg)))
  (System/exit 1))

(def ^:private status-output-lock (Object.))

(defn- println-status
  [msg]
  (locking status-output-lock
    (println msg)
    (flush)))

(defn- resolve-voice
  [vibe]
  (case vibe
    "business" "onyx"
    "crime" "onyx"
    "entertainment" "nova"
    "lifestyle" "nova"
    "news" "alloy"
    "sports" "echo"
    "technology" "alloy"
    "other" "alloy"
    "alloy"))

(defn- resolve-vibe!
  ([article-text] (resolve-vibe! article-text false))
  ([article-text verbose]
   (:vibe (style-classifier/article-text->style! article-text verbose))))

(defn- resolve-style-opts!
  [text opts verbose]
  (let [vibe (or (:vibe opts) (resolve-vibe! text verbose))
        voice (or (:voice opts) (resolve-voice vibe))]
  (when verbose
    (println-status "Assessing article vibe and style"))
    {:vibe vibe :voice voice}))

(defn run
  [{:keys [opts]}]
  ;; (println "Your opts:" opts)
  (try
    (let [article (parser/parse-article (:url opts))
          text (:text article)
          title (:title article)
          tts-future (future
                       (let [{:keys [vibe voice]} (resolve-style-opts! text opts true)]
                         (println-status (str "Creating audio for " title " with style: " vibe " and voice: " voice))
                         (audio/text-to-speech! (str title "\n\n" text) {:voice voice
                                                                         :vibe vibe})))
          difficulty-future (future
                              (println-status (str "Assessing difficulty for " title))
                              (jlpt-level/article-text->jlpt-level! text true))
          tts @tts-future
          difficulty @difficulty-future
          lesson (merge article {:status "private"
                                 :level (:lingq-level difficulty)
                                 :audio tts
                                 :original-url (or (:original-url article) (:url opts))})]
      (println "Creating lesson:")
      (println (format "  title: %s\n  url:   %s\n" (:title lesson) (:original-url lesson)))
      (lingq/create-lesson lesson))
    (catch clojure.lang.ExceptionInfo e
      (fail! (ex-message e)))))

(def dispatch-table
  [{:cmds []
    :fn run
    :doc app-doc
    :spec spec}])

(defn- print-error!
  [{:keys [msg]}]
  (binding [*out* *err*]
    (println (str "Error: " msg))
    (println)
    (println "Run `bb lingq --help` for usage."))
  (System/exit 1))

(defn -main [& args]
  (cli/dispatch
   dispatch-table
   args
   {:prog "lingq-lesson"
    :help true
    :error-fn print-error!}))

;; Only run the CLI when this file is executed directly by bb.
;; Requiring this namespace from a REPL/editor should not dispatch -main.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
