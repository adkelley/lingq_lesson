(ns lingq-lesson.category-classifier-test
  (:require [clojure.test :refer [deftest is]]
            [lingq-lesson.openai.category-classifier :as openai]
            [lingq-lesson.typesafe.category-classifier :as typesafe]
            [lingq-lesson.audio-instructions :as audio]))

(deftest providers-and-audio-share-categories
  (let [choices (into {} (map (fn [{:keys [value description]}]
                                [(keyword value) description])
                              (:choices openai/category-question)))]
    (is (= choices (:criteria typesafe/category-question)))
    (is (= 13 (count choices)))
    (is (= (set (map name (keys choices))) audio/supported-categories))
    (is (= (:instructions openai/category-question)
           (:instructions typesafe/category-question)))))
