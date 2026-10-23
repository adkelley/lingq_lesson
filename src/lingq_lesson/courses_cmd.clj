(ns lingq-lesson.courses-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(def courses-spec
  {:language {:desc "LingQ language code (Japanese only: ja)"
              :require false
              :default "ja"
              :validate #{"ja"}}})

(def courses-doc
  "List Japanese LingQ courses")

(defn- print-courses!
  [courses]
  (println (format "%-12s %s" "ID" "Title"))
  (println (apply str (repeat 60 "-")))

  (doseq [{:keys [id title]} courses]
    (println (format "%-12s %s" id title))))

(defn courses
  [{:keys [opts]}]
  (let [language (:language opts)
        response (lingq/get-my-collections language)
        courses (:results response)]
    (print-courses! courses)))
