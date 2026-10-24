(ns lingq-lesson.difficulty-classifier-test
  (:require [clojure.test :refer [deftest is]]
            [lingq-lesson.openai.jlpt-level-classifier :as openai]
            [lingq-lesson.typesafe.jlpt-level-classifier :as typesafe]))

(deftest providers-share-difficulty-rubric
  (is (= (:instructions openai/jlpt-level-question)
         (:instructions typesafe/jlpt-level-question)))
  (is (= (into {} (map (fn [{:keys [value description]}]
                         [(keyword value) description])
                       (:choices openai/jlpt-level-question)))
         (:criteria typesafe/jlpt-level-question))))
