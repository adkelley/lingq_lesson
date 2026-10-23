(ns lingq-lesson.audio-instructions-test
  (:require [clojure.test :refer [deftest is]]
            [lingq-lesson.audio-instructions :as audio]))

(deftest category-narration
  (doseq [category audio/supported-categories]
    (is (audio/supported-category? category))
    (is (seq (audio/for-category category))))
  (is (= audio/technology (audio/for-category "science-technology")))
  (is (= audio/entertainment (audio/for-category "culture")))
  (is (= audio/newscaster (audio/for-category nil)))
  (is (not (audio/supported-category? "technology"))))
