(ns lingq-lesson.lessons-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(def ^:private title-width 60)

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def lessons-spec
  {:course-id {:desc "LingQ course ID"
               :require true
               :no-doc true
               :default nil
               :validate positive-id?}})

(defn- display-width
  [ch]
  (let [code (int ch)]
    (if (or (<= 0x3000 code 0x9FFF)  ; Japanese punctuation, kana, kanji
            (<= 0xFF01 code 0xFF60)) ; Full-width Latin characters
      2
      1)))

(defn- title-column
  [text width]
  (let [text (or text "")
        truncated? (> (reduce + 0 (map display-width text)) width)
        budget (if truncated? (dec width) width)
        chars (loop [remaining (seq text)
                     result []
                     used 0]
                (if-let [ch (first remaining)]
                  (let [next-width (+ used (display-width ch))]
                    (if (<= next-width budget)
                      (recur (next remaining)
                             (conj result ch)
                             next-width)
                      result))
                  result))
        title (str (apply str chars) (when truncated? "…"))
        used (reduce + 0 (map display-width title))]
    (str title (apply str (repeat (max 0 (- width used)) " ")))))

(defn- print-lessons!
  [lessons]
  (if (seq lessons)
    (do
      (let [header
            (format "%-12s %s       %s"
                    "ID"
                    (title-column "Title" title-width)
                    "Source URL")]
        (println header)
        (println (apply str (repeat (count header) "-"))))
      (doseq [{:keys [id title source]} lessons]
        (println (format "%-12s %s       %s"
                         id
                         (title-column title title-width)
                         (or (:url source) "")))))
    (println "No lessons found.")))

(def lessons-doc
  "List the IDs, titles, and source URLs of the lessons in your LingQ course.

  <course-id> is required and must be a positive integer.")

(defn lessons
  [{{:keys [course-id]} :opts}]
  (let [lesson-list (lingq/get-my-lessons course-id)]
    (print-lessons! lesson-list)))
