#!/usr/bin/env bb

(require
 '[clojure.string :as string]
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
  (println "Usage: extract_text.bb [path_to_markdown_file] [--keep-link-text]")
  (println)
  (println "Reads Markdown from a file argument, or from stdin when no file is provided.")
  (println "Writes extracted plain text to stdout."))

(let [args *command-line-args*
      help? (some #{"--help" "-h"} args)
      flags (filter #(string/starts-with? % "-") args)
      files (remove #(string/starts-with? % "-") args)
      article (first files)
      opts {:keep-link-text? (contains? (set flags) "--keep-link-text")}]
  (when help?
    (print-help)
    (System/exit 0))

  (when (> (count files) 1)
    (exit-with-error "Usage: extract_text.bb [path_to_markdown_file] [--keep-link-text]"))

  (when (and article (not= (fs/extension article) "md"))
    (exit-with-error "File must be a markdown file"))

  (let [lines (if article
                (fs/read-all-lines article)
                (line-seq (java.io.BufferedReader. *in*)))
        cleaned (extract-text-lines lines opts)]
    (doseq [line cleaned]
      (println line))))
