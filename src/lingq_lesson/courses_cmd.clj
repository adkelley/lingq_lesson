(ns lingq-lesson.courses-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(def courses-spec
  {:language {:desc "LingQ language code"
              :require false
              :default "ja"}})

(def courses-doc
  "List LingQ courses for a language")

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
