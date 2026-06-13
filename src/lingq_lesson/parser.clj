(ns lingq-lesson.parser
  (:require
   [clojure.string :as string]
   [hato.client :as hc]
   [babashka.process :refer [shell]]))

;; Image download
(defn download-image! [url]
  (let [response (hc/get url {:as :byte-array
                              :throw-exceptions false})
        {:keys [status body]} response]
    (when-not (<= 200 status 299)
      (throw (ex-info "Failed to download image"
                      {:status status
                       :url url})))
    body))

;; Text extraction from markdown helpers
(defn- strip-markdown-blocks [markdown]
  (-> markdown
      ;; Raw embedded media/HTML from article pages is not lesson text.
      (string/replace #"(?is)<iframe\b.*?</iframe>" "")
      (string/replace #"(?is)<script\b.*?</script>" "")
      (string/replace #"(?is)<style\b.*?</style>" "")
      (string/replace #"(?is)<[^>]+>" "")

      ;; Markdown image/link markup may span lines, so remove it before line splitting.
      (string/replace #"(?s)!\[[^\]]*\]\([^)]+\)" "")
      (string/replace #"(?s)\[([^\]]*)\]\([^)]+\)" "$1")))

(defn- strip-markdown-inline [line]
  (-> line
      ;; images: always remove image markup and alt text
      (string/replace #"\!\[([^\]]*)\]\([^)]+\)" "")

      ;; links: [text](url) -> text || ""
      (string/replace #"\[([^\]]*)\]\([^)]+\)" "$1")

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

(defn- markdown-structural-line? [line]
  (boolean
   (re-matches #"^\s*(```|~~~|---+|\*\*\*+|___+)\s*$" line)))

(def ^:private sentence-boundary #"(?<=[.。!?！？])")
(def ^:private sentence-punctuation #"[.。!?！？]")

(defn- extract-sentences-xf []
  (comp
   (remove markdown-structural-line?)
   (map #(strip-markdown-inline %))
   (mapcat #(string/split % sentence-boundary))
   (map string/trim)
   (filter #(re-find sentence-punctuation %))
   (remove string/blank?)))

(defn markdown->sentences [markdown]
  (into [] (extract-sentences-xf) (string/split-lines (strip-markdown-blocks markdown))))

(defn- defuddle-exists? []
  (zero? (:exit (shell {:out :string :err :string :continue true}
                       "npx" "defuddle" "--version"))))

(defn parse-article [url]
  (when-not (defuddle-exists?)
    (throw (ex-info "defuddle not found" {})))

  (let [res (shell {:out :string :err :string :continue true}
                   "npx" "defuddle" "parse" url "--json")
        exit (:exit res 0)
        out  (:out res "")
        err  (:err res "")]
    (when-not (zero? exit)
      (throw (ex-info "Unable to parse article."
                      {:exit exit
                       :err err})))
    out))
