(ns lingq-lesson.jlpt-level-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [lingq-lesson.jlpt-level :as jlpt]))

(deftest response->output-text-uses-direct-output-text
  (is (= "{\"jlpt-level\":\"N3\"}"
         (#'jlpt/response->output-text
          {:output_text "{\"jlpt-level\":\"N3\"}"}))))

(deftest response->output-text-uses-nested-output-content
  (is (= "{\"jlpt-level\":\"N3\"}"
         (#'jlpt/response->output-text
          {:output [{:content [{:type "reasoning"
                                :text "ignored"}
                               {:type "output_text"
                                :text "{\"jlpt-level\":\"N3\"}"}]}]}))))

(deftest responses-payload-requests-json-output
  (let [payload (#'jlpt/responses-payload "これはテスト記事です。")]
    (is (= "gpt-5-mini" (:model payload)))
    (is (= {:format {:type "json_object"}} (:text payload)))
    (is (string/includes? (:input payload) "これはテスト記事です。"))))
