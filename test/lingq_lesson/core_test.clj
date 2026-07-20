(ns lingq-lesson.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.core :as core]))

(deftest crime-and-entertainment-use-recommended-voices
  (is (= "onyx" (#'core/resolve-voice "crime")))
  (is (= "nova" (#'core/resolve-voice "entertainment"))))
