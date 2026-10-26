(ns lingq-lesson.delete-cmd-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [lingq-lesson.core :as core]
            [lingq-lesson.lingq :as lingq]))

(deftest delete-course-dispatches
  (doseq [args [["2942688"] ["--course-id" "2942688"]]]
    (let [calls (atom [])]
      (with-redefs [lingq/delete-collection #(swap! calls conj %)]
        (is (str/includes? (with-out-str
                             (apply core/-main (into ["delete" "course"] args)))
                           "Deletion accepted for course 2942688."))
        (is (= [2942688] @calls))))))

(deftest delete-lesson-dispatches
  (doseq [args [["46657394"] ["--lesson-id" "46657394"]]]
    (let [calls (atom [])]
      (with-redefs [lingq/delete-lesson #(swap! calls conj %)]
        (is (str/includes? (with-out-str
                             (apply core/-main (into ["delete" "lesson"] args)))
                           "Deletion accepted for lesson 46657394."))
        (is (= [46657394] @calls))))))

(deftest delete-course-rejects-invalid-ids
  (doseq [args [[] ["0"] ["-1"] ["abc"] ["1.5"] ["9223372036854775808"]]]
    (testing (pr-str args)
      (let [errors (atom [])]
        (with-redefs [lingq/delete-collection
                      (fn [& _] (throw (AssertionError. "Unexpected deletion")))]
          (with-redefs-fn {#'core/dispatch-options
                           (assoc @#'core/dispatch-options :error-fn #(swap! errors conj %))}
            #(apply core/-main (into ["delete" "course"] args)))
          (is (seq @errors)))))))

(deftest delete-lesson-rejects-invalid-ids
  (doseq [args [[] ["0"] ["-1"] ["abc"] ["1.5"] ["9223372036854775808"]]]
    (testing (pr-str args)
      (let [errors (atom [])]
        (with-redefs [lingq/delete-lesson
                      (fn [& _] (throw (AssertionError. "Unexpected deletion")))]
          (with-redefs-fn {#'core/dispatch-options
                           (assoc @#'core/dispatch-options :error-fn #(swap! errors conj %))}
            #(apply core/-main (into ["delete" "lesson"] args)))
          (is (seq @errors)))))))

(deftest delete-help-does-not-call-api
  (with-redefs [lingq/delete-collection
                (fn [& _] (throw (AssertionError. "Unexpected deletion")))]
    (doseq [args [["--help"] ["delete" "--help"] ["delete" "course" "--help"]]]
      (is (str/includes? (with-out-str (apply core/-main args)) "course")))))

(deftest delete-lesson-help-does-not-call-api
  (with-redefs [lingq/delete-lesson
                (fn [& _] (throw (AssertionError. "Unexpected deletion")))]
    (doseq [args [["delete" "lesson" "--help"] ["delete" "--help"]]]
      (is (str/includes? (with-out-str (apply core/-main args)) "lesson")))))

(deftest delete-failure-does-not-report-success
  (let [output (java.io.StringWriter.)]
    (with-redefs [lingq/delete-collection
                  (fn [_] (throw (ex-info "Forbidden" {:status 403})))]
      (binding [*out* output]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Forbidden"
                              (core/-main "delete" "course" "2942688"))))
      (is (not (str/includes? (str output) "Deletion accepted"))))))

(deftest delete-lesson-failure-does-not-report-success
  (let [output (java.io.StringWriter.)]
    (with-redefs [lingq/delete-lesson
                  (fn [_] (throw (ex-info "Forbidden" {:status 403})))]
      (binding [*out* output]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Forbidden"
                              (core/-main "delete" "lesson" "46657394"))))
      (is (not (str/includes? (str output) "Deletion accepted"))))))
