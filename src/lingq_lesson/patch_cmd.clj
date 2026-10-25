(ns lingq-lesson.patch-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(def ^:private option->lesson-field
  {:course-id :collection
   :title :title})

(def ^:private option->course-field
   {:title :title})

(defn- opts->lesson-fields
  [opts]
  (into {}
        (keep (fn [[opt field]]
                (when-some [value (get opts opt)]
                  [field value])))
        option->lesson-field))

(defn- opts->course-fields
  [opts]
  (into {}
        (keep (fn [[opt field]]
                (when-some [value (get opts opt)]
                  [field value])))
        option->course-field))

(defn- positive-id?
  [value]
  (try
    (pos? (Long/parseLong (str value)))
    (catch NumberFormatException _
      false)))

(def patch-lesson-spec
  {:lesson-id {:desc "LingQ lesson ID"
               :require true
               :default nil
               :no-doc true
               :validate positive-id?}
   :course-id {:desc "LingQ course ID"
               :require false
               :default nil
               :validate positive-id?}
   :title {:desc "Lesson title"
           :require false
           :default nil}})

(def patch-course-spec
  {:course-id {:desc "LingQ course ID"
               :require true
               :default nil
               :no-doc true
               :validate positive-id?}
   :title {:desc "New course title"
           :require true
           :default nil}})



(def patch-lesson-doc
  "Patch a LingQ lesson with a new course-id or title

  <lesson-id> is required")

(def patch-course-doc
  "Patch a LingQ course with a new title

  <course-id> is required")


(defn patch-lesson
  [{{:keys [lesson-id] :as opts} :opts}]
  (let [fields (opts->lesson-fields opts)]
    (when (empty? fields)
      (throw (ex-info "Nothing to update. Pass at least one of --course-id or --title." {})))
    (lingq/patch-lesson lesson-id fields)
    (println (format "Updated lesson %s: %s" lesson-id (pr-str (keys fields))))))

(defn patch-course
  [{{:keys [course-id] :as opts} :opts}]
  (let [fields (opts->course-fields opts)]
    (when (empty? fields)
      (throw (ex-info "Nothing to update. Pass at least one of --course-id or --title." {})))
    (lingq/patch-course course-id fields)
    (println (format "Updated course %s: %s" course-id (pr-str (keys fields))))))
