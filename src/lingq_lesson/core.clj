(ns lingq-lesson.core
  (:require
   [babashka.cli :as cli]))

(def ^:private voices #{"ceder", "alloy", "coral"})

(defn- valid-voice?
  [voice]
  (contains? voices voice))

;; Vibe
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

(def ^:private vibes #{"newscaster"})

(defn- valid-vibe?
  [vibe]
  (contains? vibes vibe))

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
   :voice        {:desc "Voice to use"
                  :default "ceder"
                  :validate valid-voice?}
   :vibe         {:desc "Voice/style instructions"
                  :default "newscaster"
                  :validate valid-vibe?}})

(def help-spec
  (assoc option-spec
         :help {:alias :h
                :coerce :boolean
                :desc "Show help"}))

(defn run
  [{:keys [opts]}]
  (println "Your opts:" opts)
  ;; orchestrate here
  )

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
