(ns lingq-lesson.merge-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def merge-collections-spec
  {:fr-course-id {:desc "LingQ 'from' course ID"
                  :require true
                  :no-doc true
                  :default nil
                  :validate positive-id?}
   :to-course-id {:desc "LingQ 'to' course ID"
                  :require true
                  :no-doc true
                  :default nil
                  :validate positive-id?}})

(def merge-collections-doc
  "Merge two courses.

  Moves all the lessons from course 'fr' to course 'to'.

  The old course, even if it remains without any lessons, will not be deleted.

  <fr-course-id> <to-course-id> are required")

(defn merge-collections
  [{{:keys [fr-course-id to-course-id]} :opts}]
  (lingq/merge-collections fr-course-id to-course-id)
  (println (format "Merged lessons from course %s to course %s." fr-course-id to-course-id)))
