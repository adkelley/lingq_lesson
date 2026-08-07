(ns lingq-lesson.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.url-cmd :as url-cmd]))

(deftest crime-and-entertainment-use-recommended-voices
  (is (= "onyx" (#'url-cmd/resolve-voice "crime")))
  (is (= "nova" (#'url-cmd/resolve-voice "entertainment"))))
