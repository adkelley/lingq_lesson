(ns lingq-lesson.lessons-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def lessons-spec
  {:course-id {:desc "Lingq course ID"
               :require true
               :default nil
               :validate positive-id?}})

(def lessons-doc
  "List lessons in a Lingq course.")

(defn lessons
  [{:keys [opts]}]
  (let [course-id (:course-id opts)
    lesson-data (lingq/get-my-lessons course-id)]
    (prn lesson-data)))
