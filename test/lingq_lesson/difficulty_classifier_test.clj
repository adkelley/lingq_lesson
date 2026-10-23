(ns lingq-lesson.difficulty-classifier-test
  (:require [clojure.test :refer [deftest is]]
            [lingq-lesson.openai.difficulty-classifier :as openai]
            [lingq-lesson.typesafe.difficulty-classifier :as typesafe]))

(deftest providers-share-difficulty-rubric
  (is (= openai/choice->difficulty typesafe/choice->difficulty))
  (is (= (:instructions openai/difficulty-question)
         (:instructions typesafe/difficulty-question)))
  (is (= (into {} (map (fn [{:keys [value description]}]
                         [(keyword value) description])
                       (:choices openai/difficulty-question)))
         (:criteria typesafe/difficulty-question))))
