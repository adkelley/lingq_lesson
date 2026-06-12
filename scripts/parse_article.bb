#!/usr/bin/env bb

(require
 '[babashka.fs :as fs]
 '[babashka.cli :as cli]
 '[babashka.process :refer [shell]]
 '[clojure.java.io :as io])

(defn parse-webpage [url]
  (let [res (shell {:out :string :err :string} "npx" "defuddle" "parse" url "--json")
        exit (:exit res 0)
        out  (:out res "")
        err  (:err res "")]
    (when-not (zero? exit)
      (binding [*out* *err*]
        (println "defuddle error:" err))
      (throw (ex-info "defuddle failed" {:exit exit :err err})))
    out))

(defn defuddle-exists? []
  (shell {:out :string :err :string} "npx" "defuddle" "--version"))

;; CLI
;; usage/help ---------------------------------------------------------------

(def usage "Usage: parse_article.bb <url> [output.json|-]")

(defn show-help []
  (println usage)
  (println)
  (println "Parse a web article using defuddle and write structured JSON.")
  (println)
  (println "Positional arguments:")
  (println "  <url>            Required. The URL of the article to parse.")
  (println "  output.json      Optional. Path where JSON will be written.")
  (println "  -                Optional. Write JSON to stdout instead of a file.")
  (println)
  (println "Examples:")
  (println "  bb parse \"https://example.com/article\"")
  (println "  bb parse \"https://example.com/article\" my-article.json")
  (println "  bb parse \"https://example.com/article\" -            # print JSON to stdout")
  (println)
  (println "Notes:")
  (println "  - This script invokes: npx defuddle parse <url> --json")
  (println "  - Ensure npx/defuddle are available (npm i -g defuddle or use npx).")
  (println "  - On defuddle failure this script prints defuddle stderr and exits non-zero."))

;; final write logic (replace the existing output-path / spit block) ----------
(let [args *command-line-args*
      help? (some #{"--help" "-h"} args)
      url (first args)]

  (when help?
    (show-help)
    (System/exit 0))

  (when (nil? (defuddle-exists?))
    (println "defuddle is not installed.")
    (System/exit 1))

  (when (nil? url)
    (println "url is required.")
    (System/exit 1))

  (let [output-path (if (nil? (second args)) "article.json" (second args))
        json (parse-webpage url)]
    (if (= output-path "-")
      (do (println json)
          (flush))
      (do (spit output-path json)
          (println "Parsed JSON written to" output-path)))))
