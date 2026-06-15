(ns lingq-lesson.parser
  (:require
   [clojure.string :as string]
   [cheshire.core :as json]
   [hato.client :as hc]
   [babashka.process :refer [shell]]))

;; ---------------------------------------------------------------------------
;; Constants
;; ---------------------------------------------------------------------------
(def ^:private sentence-boundary #"(?<=[.。!?！？])")
(def ^:private sentence-punctuation #"[.。!?！？]")

;; ---------------------------------------------------------------------------
;; Utilities
;; ---------------------------------------------------------------------------
(defn- fail!
  [msg]
  (binding [*out* *err*]
    (println (str "Error: " msg)))
  (System/exit 1))

;; ---------------------------------------------------------------------------
;; Defuddle / fetch helpers
;; ---------------------------------------------------------------------------
(defn- defuddle-exists? []
  (zero? (:exit (shell {:out :string :err :string :continue true}
                       "npx" "defuddle" "--version"))))

(defn- fetch-defuddle-json [url]
  (when-not (defuddle-exists?)
    (throw (ex-info "defuddle not found" {})))

  (let [res (shell {:out :string :err :string :continue true}
                   "npx" "defuddle" "parse" url "--json")
        exit (:exit res 0)
        out  (:out res "")
        err  (:err res "")]
    (when-not (zero? exit)
      (throw (ex-info "Unable to fetch article using defuddle."
                      {:exit exit
                       :err err})))
    out))

(defn- parse-defuddle-json [out]
  (try
    (json/parse-string out true)
    (catch Exception e
      (fail! (format "Failed to decode article to JSON: %s" (.getMessage e))))))

;; ---------------------------------------------------------------------------
;; Image helper (returns byte-array body)
;; ---------------------------------------------------------------------------
(defn download-image! [url]
  (let [response (hc/get url {:as :byte-array
                              :throw-exceptions false})
        {:keys [status body]} response]
    (when-not (<= 200 status 299)
      (throw (ex-info "Failed to download image"
                      {:status status
                       :url url})))
    body))

;; ---------------------------------------------------------------------------
;; Markdown -> sentence extraction helpers
;; ---------------------------------------------------------------------------
(defn- strip-markdown-blocks [markdown]
  (-> markdown
      ;; Remove embedded HTML/iframe/script/style blocks and any tags
      (string/replace #"(?is)<iframe\b.*?</iframe>" "")
      (string/replace #"(?is)<script\b.*?</script>" "")
      (string/replace #"(?is)<style\b.*?</style>" "")
      (string/replace #"(?is)<[^>]+>" "")

      ;; Remove multi-line Markdown image/link constructs before line splitting
      (string/replace #"(?s)!\[[^\]]*\]\([^)]+\)" "")
      (string/replace #"(?s)\[([^\]]*)\]\([^)]+\)" "$1")))

(defn- strip-markdown-inline [line]
  (-> line
      ;; remove images (keep nothing)
      (string/replace #"\!\[([^\]]*)\]\([^)]+\)" "")

      ;; links: [text](url) -> text
      (string/replace #"\[([^\]]*)\]\([^)]+\)" "$1")

      ;; emphasis/code markers
      (string/replace #"(\*\*|__|\*|_|`)" "")

      ;; blockquote markers
      (string/replace #"^\s*>\s?" "")

      ;; list markers
      (string/replace #"^\s*[-*+]\s+" "")
      (string/replace #"^\s*\d+\.\s+" "")

      ;; headings
      (string/replace #"^\s*#{1,6}\s+" "")

      string/trim))

(defn- markdown-structural-line? [line]
  (boolean (re-matches #"^\s*(```|~~~|---+|\*\*\*+|___+)\s*$" line)))

(defn- extract-sentences-xf []
  (comp
   (remove markdown-structural-line?)
   (map #(strip-markdown-inline %))
   (mapcat #(string/split % sentence-boundary))
   (map string/trim)
   (filter #(re-find sentence-punctuation %))
   (remove string/blank?)))

(defn- markdown->sentences [markdown]
  (into [] (extract-sentences-xf) (string/split-lines (strip-markdown-blocks markdown))))

(defn- markdown->text [content-markdown]
  (string/join "\n" (markdown->sentences content-markdown)))

(defn- defuddle->article
  "Map parsed defuddle output into canonical article map"
  [parsed]
  (let [content-markdown (or (:contentMarkdown parsed) "")]
    {:text (markdown->text content-markdown)
     :title (or (:title parsed) "")
     :image (download-image! (or (:image parsed) ""))
     :description (or (:description parsed) "")}))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------
(defn parse-article
  "Parses an article from the given URL using defuddle.
  The shape returned is a map with keys :text, :title, :image, and :description."
  [url]
  (-> url
      fetch-defuddle-json
      parse-defuddle-json
      defuddle->article))
