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
  (println "Usage: extract_text.bb <path_to_markdown_file> [--keep-link-text]"))

(let [args *command-line-args*
      help? (some #{"--help" "-h"} args)
      [article & flags] args
      opts {:keep-link-text? (contains? (set flags) "--keep-link-text")}]
  (when help?
    (print-help)
    (System/exit 0))

  (when-not article
    (exit-with-error "Usage: extract_text.bb <path_to_markdown_file>"))

  (when-not (= (fs/extension article) "md")
    (exit-with-error "File must be a markdown file"))

  (let [filename (str (fs/strip-ext article) ".txt")
        lines (fs/read-all-lines article)
        cleaned (extract-text-lines lines opts)]
    (fs/write-lines filename cleaned)))
