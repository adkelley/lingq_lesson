(ns lingq-lesson.patch-cmd-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [lingq-lesson.core :as core]
   [lingq-lesson.lingq :as lingq]))

(deftest patch-cli-updates-supplied-fields
  (doseq [[flags expected]
          [[["--course-id" "2948600"] {:collection 2948600}]
           [["--title" "新しいタイトル"] {:title "新しいタイトル"}]
           [["--course-id" "2948600" "--title" "New title"]
            {:collection 2948600 :title "New title"}]]]
    (testing (pr-str flags)
      (let [calls (atom [])]
        (with-redefs [lingq/patch-lesson
                      (fn [id fields] (swap! calls conj [id fields]))]
          (let [output (with-out-str
                         (apply core/-main (into ["patch" "46657394"] flags)))]
            (is (= [[46657394 expected]] @calls))
            (is (str/includes? output "Updated lesson 46657394"))))))))

(deftest patch-cli-requires-update-fields
  (with-redefs [lingq/patch-lesson
                (fn [& _] (throw (AssertionError. "Unexpected LingQ call")))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Nothing to update"
                          (core/-main "patch" "46657394")))))

(deftest patch-cli-rejects-missing-or-invalid-ids
  (doseq [args [["--title" "New title"]
                ["0" "--title" "New title"]
                ["-1" "--title" "New title"]
                ["abc" "--title" "New title"]
                ["46657394" "--course-id" "0"]
                ["46657394" "--course-id" "-1"]
                ["46657394" "--course-id" "abc"]]]
    (testing (pr-str args)
      (let [errors (atom [])]
        (with-redefs [lingq/patch-lesson
                      (fn [& _] (throw (AssertionError. "Unexpected LingQ call")))]
          (with-redefs-fn {#'core/dispatch-options
                           (assoc @#'core/dispatch-options
                                  :error-fn #(swap! errors conj %))}
            #(apply core/-main (into ["patch"] args)))
          (is (seq @errors)))))))
