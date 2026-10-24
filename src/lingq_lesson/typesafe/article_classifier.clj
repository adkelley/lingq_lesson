(ns lingq-lesson.typesafe.article-classifier
  (:require [lingq-lesson.typesafe :as typesafe]
            [lingq-lesson.typesafe.jlpt-level-classifier :as jlpt-level-classifier]
            [lingq-lesson.typesafe.category-classifier :as category-classifier]))

(defn article-text->classification!
  [article-text]
  (let [response (typesafe/questions
                  {:state article-text
                   :questions {:jlpt-level jlpt-level-classifier/jlpt-level-question
                               :category category-classifier/category-question}})
        jlpt-level-choice (get-in response [:answers :jlpt-level :choice])
        category-choice (get-in response [:answers :category :choice])]
    {:jlpt-level jlpt-level-choice
     :category category-choice}))
