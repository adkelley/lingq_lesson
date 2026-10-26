(ns lingq-lesson.merge-cmd-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [lingq-lesson.core :as core]
   [lingq-lesson.lingq :as lingq]))

(deftest merge-cli-passes-source-and-destination-in-order
  (let [calls (atom [])]
    (with-redefs [lingq/merge-collections
                  (fn [from to] (swap! calls conj [from to]))]
      (let [output (with-out-str (core/-main "merge" "123456" "2948600"))]
        (is (= [[123456 2948600]] @calls))
        (is (str/includes? output "Merged lessons from course 123456 to course 2948600."))))))

(deftest merge-cli-rejects-missing-or-invalid-course-ids
  (doseq [args [[] ["123456"]
                ["0" "2948600"] ["-1" "2948600"] ["abc" "2948600"]
                ["123456" "0"] ["123456" "-1"] ["123456" "abc"]]]
    (testing (pr-str args)
      (let [errors (atom [])]
        (with-redefs [lingq/merge-collections
                      (fn [& _] (throw (AssertionError. "Unexpected merge")))]
          (with-redefs-fn {#'core/dispatch-options
                           (assoc @#'core/dispatch-options
                                  :error-fn #(swap! errors conj %))}
            #(apply core/-main (into ["merge"] args)))
          (is (seq @errors)))))))

(deftest merge-cli-help-does-not-merge
  (with-redefs [lingq/merge-collections
                (fn [& _] (throw (AssertionError. "Unexpected merge")))]
    (let [output (with-out-str (core/-main "merge" "--help"))]
      (is (str/includes? output "Merge two courses"))
      (is (str/includes? output "<fr-course-id> <to-course-id>")))))

(deftest merge-cli-does-not-report-success-after-failure
  (with-redefs [lingq/merge-collections
                (fn [& _] (throw (ex-info "Move failed" {})))]
    (let [output (with-out-str
                   (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Move failed"
                                         (core/-main "merge" "123456" "2948600"))))]
      (is (not (str/includes? output "Merged lessons"))))))

(deftest merge-collections-moves-every-lesson-by-id
  (let [calls (atom [])]
    (with-redefs [lingq/get-my-lessons
                  (fn [id]
                    (swap! calls conj [:fetch id])
                    [{:id 101 :title "First"} {:id 202 :title "Second"}])
                  lingq/patch-lesson
                  (fn [id fields] (swap! calls conj [:patch id fields]))]
      (lingq/merge-collections 123456 2948600)
      (is (= [[:fetch 123456]
              [:patch 101 {:collection 2948600}]
              [:patch 202 {:collection 2948600}]]
             @calls)))))

(deftest merge-collections-handles-an-empty-source
  (with-redefs [lingq/get-my-lessons (constantly [])
                lingq/patch-lesson
                (fn [& _] (throw (AssertionError. "Unexpected lesson update")))]
    (is (= "No lessons to merge.\n"
           (with-out-str (lingq/merge-collections 123456 2948600))))))

(deftest merge-collections-stops-after-a-failed-move
  (let [calls (atom [])
        failure (ex-info "Move failed" {:status 403})]
    (with-redefs [lingq/get-my-lessons (constantly [{:id 101} {:id 202} {:id 303}])
                  lingq/patch-lesson
                  (fn [id _]
                    (swap! calls conj id)
                    (when (= id 202) (throw failure)))]
      (is (identical? failure
                      (try
                        (lingq/merge-collections 123456 2948600)
                        (catch clojure.lang.ExceptionInfo e e))))
      (is (= [101 202] @calls)))))
