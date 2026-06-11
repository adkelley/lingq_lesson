#!/usr/bin/env bb

(require
 '[clojure.string :as string]
 '[babashka.fs :as fs]
 '[cheshire.core :as json])

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn markdown-structural-line? [line]
  (boolean
   (re-matches #"^\s*(```|~~~|---+|\*\*\*+|___+)\s*$" line)))

(def markdown-image-pattern #"\!\[([^\]]*)\]\(([^)]+)\)")

(defn extract-markdown-image [line]
  (when-let [[_ alt url] (re-find markdown-image-pattern line)]
    {:alt alt
     :url url}))

(defn with-indexes [images]
  (map-indexed (fn [idx image] (assoc image :index idx)) images))

(defn extract-image-lines [lines]
  (->> lines
       (remove markdown-structural-line?)
       (keep extract-markdown-image)
       with-indexes
       vec))

(defn print-images [images]
  (println (json/generate-string images {:pretty true})))

(def usage "Usage: extract_images.bb [path_to_markdown_file]")

(defn print-help []
  (println usage)
  (println)
  (println "Reads Markdown from a file argument, or from stdin when no file is provided.")
  (println "Extracts Markdown image links and writes structured JSON to stdout."))

(let [args *command-line-args*
      help? (some #{"--help" "-h"} args)
      files (remove #(string/starts-with? % "-") args)
      article (first files)]
  (when help?
    (print-help)
    (System/exit 0))

  (when (> (count files) 1)
    (exit-with-error usage))

  (when (and article (not= (fs/extension article) "md"))
    (exit-with-error "File must be a markdown file"))

  (let [lines (if article
                (fs/read-all-lines article)
                (line-seq (java.io.BufferedReader. *in*)))
        images (extract-image-lines lines)]
    (print-images images)))
