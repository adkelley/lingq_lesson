(ns lingq-lesson.typesafe.article-classifier
  (:require [lingq-lesson.typesafe :as typesafe]
            [lingq-lesson.typesafe.difficulty-classifier :as difficulty-classifier]
            [lingq-lesson.typesafe.category-classifier :as category-classifier]))

(defn article-text->classification!
  [article-text]
  (let [response (typesafe/questions
                  {:state article-text
                   :questions {:difficulty difficulty-classifier/difficulty-question
                               :category category-classifier/category-question}})
        difficulty-choice (get-in response [:answers :difficulty :choice])
        category-choice (get-in response [:answers :category :choice])]
    {:difficulty (difficulty-classifier/choice->difficulty difficulty-choice)
     :category category-choice}))
