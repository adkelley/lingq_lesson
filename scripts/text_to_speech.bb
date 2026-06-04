#!/usr/bin/env bb

(require
 '[clojure.string :as string]
 '[babashka.cli :as cli]
 '[babashka.fs :as fs])

;; CLI
(def voices #{"alloy" "ash" "ballad" "coral" "echo"
              "fable" "nova" "onyx" "sage" "shimmer" "verse"})

(defn- valid-voice?
  [voice]
  (contains? voices voice))

(def supported-formats #{"mp3" "wav"})

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn- valid-audio-format? [path]
  (contains? supported-formats
             (string/lower-case (str (fs/extension path)))))

(def cli-spec
  {:restrict false
   :spec
   {:output       {:alias :o
                   :desc "Output audio file path"
                   :validate valid-audio-format?
                   :require true}
    :voice        {:alias :v
                   :default "alloy"
                   :validate valid-voice?
                   :desc "Voice to use"}
    :instructions {:alias :i
                   :desc "Voice/style instructions"
                   :default "Read like a calm news narrator"}
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

(def usage
  "Usage: text_to_speech.bb [path_to_text_file] --output <audio_file> [options]")

(defn help?
  [args]
  (some #{"--help" "-h"} args))

(defn show-help
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

(let [{:keys [args opts]} (cli/parse-args *command-line-args* cli-spec)
      input-file (first args)]
  (when (> (count args) 1)
    (exit-with-error usage))

  (let [text (if input-file
               (slurp input-file)
               (slurp *in*))]

  ;; TODO: call OpenAI TTS and write (:output opts)
    (println (format "Would write %d characters to %s"
                     (count text)
                     (:output opts)))))
