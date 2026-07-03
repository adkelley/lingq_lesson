(ns lingq-lesson.audio-instructions-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.audio-instructions :as audio-instructions]))

(deftest supported-vibe-recognizes-public-vibes
  (is (audio-instructions/supported-vibe? "news"))
  (is (audio-instructions/supported-vibe? "sports"))
  (is (audio-instructions/supported-vibe? "lifestyle"))
  (is (not (audio-instructions/supported-vibe? "default")))
  (is (not (audio-instructions/supported-vibe? "unknown"))))

(deftest for-vibe-returns-matching-instructions
  (is (= audio-instructions/newscaster
         (audio-instructions/for-vibe "news")))
  (is (= audio-instructions/sportscaster
         (audio-instructions/for-vibe "sports")))
  (is (= audio-instructions/lifestyle
         (audio-instructions/for-vibe "lifestyle"))))

(deftest for-vibe-falls-back-to-newscaster
  (is (= audio-instructions/newscaster
         (audio-instructions/for-vibe nil)))
  (is (= audio-instructions/newscaster
         (audio-instructions/for-vibe "unknown"))))
