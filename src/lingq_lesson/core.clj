(ns lingq-lesson.core
  (:require
   [babashka.deps :as deps]
   [lingq-lesson.courses-cmd :as courses-cmd]
   [lingq-lesson.lessons-cmd :as lessons-cmd]
   [lingq-lesson.patch-cmd :as patch-cmd]
   [lingq-lesson.url-cmd :as url-cmd]))

;; bb currently bundles an older babashka.cli; add and reload the newer
;; version so automatic help/completions are available before dispatch.
;; Remove this once Babashka ships with a compatible version.
(deps/add-deps '{:deps {org.babashka/cli {:mvn/version "0.12.85"}}})
(require '[babashka.cli :as cli] :reload)

(def ^:private dispatch-table
  [{:cmds ["courses"]
    :fn courses-cmd/courses
    :doc courses-cmd/courses-doc
    :args->opts [:language]
    :spec courses-cmd/courses-spec}

   {:cmds ["lessons"]
    :fn lessons-cmd/lessons
    :doc lessons-cmd/lessons-doc
    :args->opts [:course-id]
    :spec lessons-cmd/lessons-spec}

   {:cmds ["patch"]
    :fn patch-cmd/patch
    :doc patch-cmd/patch-doc
    :args->opts [:lesson-id]
    :spec patch-cmd/patch-spec}

   {:cmds ["url"]
    :fn url-cmd/url
    :doc url-cmd/url-doc
    :args->opts [:url]
    :spec url-cmd/url-spec}])

(defn- print-error!
  [{:keys [msg]}]
  (binding [*out* *err*]
    (println (str "Error: " msg))
    (println)
    (println "Run `bb lingq --help` for usage."))
  (System/exit 1))

(def ^:private dispatch-options
  {:prog "lingq-lesson"
   :help true
   :error-fn print-error!})

(defn -main [& args]
  (cli/dispatch dispatch-table args dispatch-options))

;; Only run the CLI when this file is executed directly by bb.
;; Requiring this namespace from a REPL/editor should not dispatch -main.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
