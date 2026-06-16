(ns lingq-lesson.lingq-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.lingq :as lingq]))

(defn- part-content [multipart name]
  (:content (first (filter #(= name (:name %)) multipart))))

(deftest multipart-parts-includes-original-url
  (let [multipart (#'lingq/multipart-parts {:title "Title"
                                            :text "Text"
                                            :status "private"
                                            :image "image-bytes"
                                            :level 3
                                            :tags ["news"]
                                            :description "Description"
                                            :original-url "https://example.com/article"})]
    (is (= "https://example.com/article"
           (part-content multipart "originalUrl")))))
