(ns test-runner
  (:require
   [clojure.test :as test]
   [lingq-lesson.audio-instructions-test]
   [lingq-lesson.text-to-speech-test]
   [lingq-lesson.courses-cmd-test]
   [lingq-lesson.lessons-cmd-test]
   [lingq-lesson.core-test]
   [lingq-lesson.difficulty-classifier-test]
   [lingq-lesson.lingq-test]
   [lingq-lesson.openai-test]
   [lingq-lesson.parser-test]
   [lingq-lesson.category-classifier-test]))

(defn run []
  (let [{:keys [fail error]} (test/run-tests 'lingq-lesson.audio-instructions-test
                                             'lingq-lesson.text-to-speech-test
                                             'lingq-lesson.courses-cmd-test
                                             'lingq-lesson.lessons-cmd-test
                                             'lingq-lesson.core-test
                                             'lingq-lesson.difficulty-classifier-test
                                             'lingq-lesson.lingq-test
                                             'lingq-lesson.openai-test
                                             'lingq-lesson.parser-test
                                             'lingq-lesson.category-classifier-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
