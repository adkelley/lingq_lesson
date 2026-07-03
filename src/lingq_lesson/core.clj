(ns lingq-lesson.core
  (:require
   [babashka.cli :as cli]
   [lingq-lesson.audio :as audio]
   [lingq-lesson.audio-instructions :as audio-instructions]
   [lingq-lesson.lingq :as lingq]
   [lingq-lesson.jlpt-level :as jlpt-level]
   [lingq-lesson.parser :as parser]))

(defn valid-url?
  [s]
  (try
    (let [uri (java.net.URI. s)]
      (and (#{"http" "https"} (.getScheme uri))
           (some? (.getHost uri))))
    (catch Exception _
      false)))

(def app-doc "Create a Linq lesson from an article URL.")

(def option-spec
  {:url          {:desc "URL article"
                  :require true
                  :validate {:pred valid-url?
                             :ex-msg (fn [{:keys [value]}]
                                       (str "Invalid URL: " value))}}
   :voice        {:desc "Voice to use (alloy, echo, nova, onyx, shimmer)"
                  :default audio/default-voice
                  :validate audio/supported-voice?}
   :vibe         {:desc "Voice/style instructions (news, sports, lifestyle)"
                  :default "news"
                  :validate audio-instructions/supported-vibe?}})

(def help-spec
  (assoc option-spec
         :help {:alias :h
                :coerce :boolean
                :desc "Show help"}))

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

(defn run
  [{:keys [opts]}]
  ;; (println "Your opts:" opts)
  (try
    (let [article (parser/parse-article (:url opts))
          text (:text article)
          title (:title article)
          tts-future (future
                       (println-status (str "Creating audio for " title))
                       (audio/text-to-speech! (str title "\n\n" text) {:voice (:voice opts)
                                                                       :vibe (:vibe opts)}))
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
    :spec option-spec}])

(defn- help?
  [args]
  (some #{"--help" "-h"} args))

(defn- print-help
  []
  (println "Usage: linq_lesson [options]")
  (println)
  (println app-doc)
  (println)
  (println "Options:")
  (println (cli/format-opts {:spec help-spec})))

(defn- print-error!
  [{:keys [msg]}]
  (binding [*out* *err*]
    (println (str "Error: " msg))
    (println)
    (println "Run `bb lingq --help` for usage."))
  (System/exit 1))

(defn -main [& args]
  (if (help? args)
    (print-help)
    (cli/dispatch
     dispatch-table
     args
     {:prog "linq-lesson"
      :help true
      :error-fn print-error!})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
