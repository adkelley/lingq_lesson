(ns lingq-lesson.courses-cmd-test
  (:require
   [clojure.test :refer [deftest is]]
   [lingq-lesson.courses-cmd :as courses-cmd]
   [lingq-lesson.lingq :as lingq]))

(deftest courses-prints-course-ids-and-titles
  (let [requested-language (atom nil)
        output (with-redefs [lingq/get-my-collections
                             (fn [language]
                               (reset! requested-language language)
                               {:count 2
                                :next nil
                                :previous nil
                                :results [{:id 123456 :title "Japanese Politics"}
                                          {:id 234567 :title "Japanese News"}]})]
                 (with-out-str
                   (courses-cmd/courses {:opts {:language "ja"}})))]
    (is (= "ja" @requested-language))
    (is (re-find #"ID" output))
    (is (re-find #"Title" output))
    (is (re-find #"123456" output))
    (is (re-find #"Japanese Politics" output))
    (is (re-find #"234567" output))
    (is (re-find #"Japanese News" output))))
