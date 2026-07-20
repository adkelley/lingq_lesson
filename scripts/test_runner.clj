(ns test-runner
  (:require
   [clojure.test :as test]
   [lingq-lesson.audio-instructions-test]
   [lingq-lesson.audio-test]
   [lingq-lesson.core-test]
   [lingq-lesson.jlpt-level-test]
   [lingq-lesson.lingq-test]
   [lingq-lesson.openai-test]
   [lingq-lesson.parser-test]
   [lingq-lesson.style-classifier-test]))

(defn run []
  (let [{:keys [fail error]} (test/run-tests 'lingq-lesson.audio-instructions-test
                                             'lingq-lesson.audio-test
                                             'lingq-lesson.core-test
                                             'lingq-lesson.jlpt-level-test
                                             'lingq-lesson.lingq-test
                                             'lingq-lesson.openai-test
                                             'lingq-lesson.parser-test
                                             'lingq-lesson.style-classifier-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
