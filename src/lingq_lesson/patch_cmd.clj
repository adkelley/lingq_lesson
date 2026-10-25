(ns lingq-lesson.patch-cmd
  (:require [lingq-lesson.lingq :as lingq]))

(def ^:private option->field
  {:course-id :collection
   :title :title})

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
               :no-doc true
               :validate positive-id?}
   :course-id {:desc "LingQ course ID"
               :require false
               :default nil
               :validate positive-id?}
   :title {:desc "Lesson title"
           :require false
           :default nil}})

(defn- opts->fields
  [opts]
  (into {}
        (keep (fn [[opt field]]
                (when-some [value (get opts opt)]
                  [field value])))
        option->field))

(def patch-doc
  "Patch a LingQ lesson with a new course-id or title

  <lesson-id> is required")

(defn patch
  [{{:keys [lesson-id] :as opts} :opts}]
  (let [fields (opts->fields opts)]
    (when (empty? fields)
      (throw (ex-info "Nothing to update. Pass at least one of --course-id or --title." {})))
    (lingq/patch-lesson lesson-id fields)
    (println (format "Updated lesson %s: %s" lesson-id (pr-str (keys fields))))))
