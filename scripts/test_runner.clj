(ns test-runner
  (:require
   [clojure.test :as test]
   [lingq-lesson.lingq-test]))

(defn run []
  (let [{:keys [fail error]} (test/run-tests 'lingq-lesson.lingq-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
