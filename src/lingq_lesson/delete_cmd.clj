(ns lingq-lesson.delete-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def delete-collection-spec
  {:course-id {:desc "LingQ course ID"
               :require true
               :no-doc true
               :default nil
               :validate positive-id?}})

(def delete-collection-doc
  "Request deletion of a Japanese LingQ course.

  <course-id> is required")

(defn delete-collection
  [{{:keys [course-id]} :opts}]
  (lingq/delete-collection course-id)
  (println (format "Deletion accepted for course %s." course-id)))

(def delete-lesson-spec
  {:lesson-id {:desc "LingQ lesson ID"
               :require true
               :no-doc true
               :default nil
               :validate positive-id?}})

(def delete-lesson-doc
  "Request deletion of a Japanese LingQ lesson.

  <lesson-id> is required")

(defn delete-lesson
  [{{:keys [lesson-id]} :opts}]
  (lingq/delete-lesson lesson-id)
  (println (format "Deletion accepted for lesson %s." lesson-id)))
