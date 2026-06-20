(ns test-runner
  (:require
   [clojure.test :as test]
   [lingq-lesson.jlpt-level-test]
   [lingq-lesson.lingq-test]
   [lingq-lesson.openai-test]
   [lingq-lesson.parser-test]))

(defn run []
  (let [{:keys [fail error]} (test/run-tests 'lingq-lesson.jlpt-level-test
                                             'lingq-lesson.lingq-test
                                             'lingq-lesson.openai-test
                                             'lingq-lesson.parser-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
