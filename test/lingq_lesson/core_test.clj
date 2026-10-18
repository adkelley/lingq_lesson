(ns lingq-lesson.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.url-cmd :as url-cmd]))

(deftest crime-and-entertainment-use-recommended-voices
  (is (= "onyx" (#'url-cmd/resolve-voice "crime")))
  (is (= "nova" (#'url-cmd/resolve-voice "entertainment"))))

(deftest course-id-validation
  (is (#'url-cmd/positive-course-id? "123456"))
  (is (#'url-cmd/positive-course-id? 123456))
  (is (not (#'url-cmd/positive-course-id? nil)))
  (is (not (#'url-cmd/positive-course-id? "")))
  (is (not (#'url-cmd/positive-course-id? "abc")))
  (is (not (#'url-cmd/positive-course-id? "0")))
  (is (not (#'url-cmd/positive-course-id? "-1"))))
