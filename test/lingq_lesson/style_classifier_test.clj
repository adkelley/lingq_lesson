(ns lingq-lesson.style-classifier-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [lingq-lesson.style-classifier :as style-classifier]))

(deftest prompt-lists-crime-and-entertainment-categories
  (let [prompt (style-classifier/prompt "article text")]
    (is (str/includes? prompt "  * crime\n"))
    (is (str/includes? prompt "  * entertainment\n"))))
