(ns lingq-lesson.patch-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def patch-spec
  {:lesson-id {:desc "LingQ lesson ID"
               :require true
               :default nil
               :validate positive-id?}
   :course-id {:desc "LingQ course ID"
               :require true
               :default nil
               :validate positive-id?}})

(def patch-doc
  "Patch a LingQ lesson with a new course-id")

(defn patch
  [{:keys [opts]}]
  (let [lesson-id (:lesson-id opts)
        course-id (:course-id opts)
        response (lingq/patch-lesson lesson-id course-id)]
    (println (format "Moved lesson %s to course %s" lesson-id (get-in response [:collection :title])))))
