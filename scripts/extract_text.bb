#!/usr/bin/env bb

(require
 '[clojure.string :as string]
 '[cheshire.core :as json]
 '[babashka.fs :as fs])

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn strip-markdown-inline [line {:keys [keep-link-text?]}]
  (-> line
      ;; images: always remove image markup and alt text
      (string/replace #"\!\[([^\]]*)\]\([^)]+\)" "")

      ;; links: [text](url) -> text || ""
      (string/replace #"\[([^\]]+)\]\([^)]+\)"
                      (if keep-link-text? "$1" ""))

      ;; bold/italic/code markers
      (string/replace #"(\*\*|__|\*|_|`)" "")

      ;; leading blockquote marker
      (string/replace #"^\s*>\s?" "")

      ;; leading unordered list marker
      (string/replace #"^\s*[-*+]\s+" "")

      ;; leading ordered list marker
      (string/replace #"^\s*\d+\.\s+" "")

      ;; leading heading marker: # Title -> Title
      (string/replace #"^\s*#{1,6}\s+" "")

      string/trim))

(defn markdown-structural-line? [line]
  (boolean
   (re-matches #"^\s*(```|~~~|---+|\*\*\*+|___+)\s*$" line)))

(def sentence-boundary #"(?<=[.。!?！？])")

(defn extract-text-xf [opts]
  (comp
   (remove markdown-structural-line?)
   (map #(strip-markdown-inline % opts))
   (mapcat #(string/split % sentence-boundary))
   (map string/trim)
   (remove string/blank?)))

(defn extract-text-lines [lines opts]
  (into [] (extract-text-xf opts) lines))

(defn print-help []
  (println "Usage: extract_text.bb [path_to_json_file] [--keep-link-text]")
  (println)
  (println "Reads Markdown from a JSON object, either from a file or from stdin when no file is provided.")
  (println "JSON object must have a 'contentMarkdown' field.")
  (println "Writes extracted plain text to stdout."))

(let [args *command-line-args*
      help? (some #{"--help" "-h"} args)
      flags (filter #(string/starts-with? % "-") args)
      files (remove #(string/starts-with? % "-") args)
      json-file (first files)
      opts {:keep-link-text? (contains? (set flags) "--keep-link-text")}]
  (when help?
    (print-help)
    (System/exit 0))

  (when (> (count files) 1)
    (exit-with-error "Usage: extract_text.bb [path_to_json_file] [--keep-link-text]"))

  (when (and json-file (not= (fs/extension json-file) "json"))
    (exit-with-error "File must be a json file"))

  (let [json-str (if json-file
                   (slurp json-file)
                   (slurp *in*))
        parsed (try
                 (json/parse-string json-str true)
                 (catch Exception e
                   (exit-with-error (format "Failed to parse JSON: %s" (.getMessage e)))))
        md (:contentMarkdown parsed)]
    (when-not (string? md)
      (exit-with-error "JSON must contain a 'contentMarkdown' field containing the article text"))

    (let [lines (string/split-lines md)
          cleaned (extract-text-lines lines opts)]
      (doseq [line cleaned]
        (println line)))))
