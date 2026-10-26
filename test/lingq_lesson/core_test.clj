(ns lingq-lesson.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [lingq-lesson.url-cmd :as url]
            [lingq-lesson.core :as core]
            [lingq-lesson.parser :as parser]
            [lingq-lesson.openai.article-classifier :as openai]
            [lingq-lesson.typesafe.article-classifier :as typesafe]
            [lingq-lesson.openai.text-to-speech :as tts]
            [lingq-lesson.lingq :as lingq]))

(deftest classifier-dispatch-category-and-overrides
  (doseq [provider ["openai" "typesafe"]
          dry-run [true false]
          override-level [nil "N2"]]
    (let [calls (atom [])
          classify (fn [text]
                     (is (= "本文" text))
                     {:category "science-technology" :jlpt-level "N3"})]
      (with-redefs [parser/parse-article (fn [_ image options]
                                           (is (= "https://example.com/cover" image))
                                           (is (= (not dry-run) (:download-image? options)))
                                           {:title "Title" :text "本文"})
                    openai/article-text->classification! (fn [text] (swap! calls conj "openai") (classify text))
                    typesafe/article-text->classification! (fn [text] (swap! calls conj "typesafe") (classify text))
                    lingq/get-my-collections (fn [language]
                                               (is (= "ja" language))
                                               (swap! calls conj :collections)
                                               {:results [{:id 123 :title "Culture"}]})
                    tts/text-to-speech! (fn [_ opts]
                                          (is (= {:category "culture" :voice "onyx"} opts))
                                          (swap! calls conj :audio) :audio)
                    lingq/create-lesson (fn [lesson]
                                          (is (= (if override-level 4 3) (:level lesson)))
                                          (is (= :audio (:audio lesson)))
                                          (is (= 123 (:course-id lesson)))
                                          (swap! calls conj :upload))]
        (let [output (with-out-str
                       (#'url/create-lesson! {:url "https://example.com/article"
                                              :image "https://example.com/cover"
                                              :classifier provider :dry-run dry-run
                                              :category "culture" :voice "onyx" :jlpt-level override-level}))]
          (is (= (cond-> (if override-level [] [provider])
                   (not dry-run) (into [:collections :audio :upload]))
                 @calls))
          (is (str/includes? output "Category: culture"))
          (is (str/includes? output (str "Lingq Level: " (if override-level 4 3))))
          (is (str/includes? output (str "JLPT Level: " (or override-level "N3"))))
          (when-not dry-run
            (is (< (str/index-of output "JLPT Level:")
                   (str/index-of output "Creating audio")))))))))

(deftest recommended-voices
  (is (= "alloy" (#'url/resolve-voice "science-technology")))
  (is (= "nova" (#'url/resolve-voice "culture"))))

(deftest cli-selects-only-the-requested-classifier
  (doseq [[flags expected category level]
          [[[] "openai" "science-technology" 3]
           [["--classifier" "typesafe"] "typesafe" "health" 4]
           [["--classifier" "typesafe" "--jlpt-level" "n3"] "typesafe" "health" 3]
           [["--classifier" "openai"] "openai" "science-technology" 3]]]
    (testing (str "classifier flags: " flags)
      (let [calls (atom [])
            classify (fn [provider result text]
                       (swap! calls conj provider)
                       (is (= "本文" text))
                       result)]
        (with-redefs [parser/parse-article (fn [& _] {:title "Title" :text "本文"})
                      openai/article-text->classification!
                      (partial classify "openai"
                               {:category "science-technology" :jlpt-level "N3"})
                      typesafe/article-text->classification!
                      (partial classify "typesafe"
                               {:category "health" :jlpt-level "N2"})
                      tts/text-to-speech! (fn [& _] (throw (ex-info "Unexpected audio" {})))
                      lingq/create-lesson (fn [& _] (throw (ex-info "Unexpected upload" {})))]
          (let [output (with-out-str
                         (apply core/-main
                                (into ["url" "https://example.com/article" "--dry-run"] flags)))]
            (is (= [expected] @calls) "Only the selected provider should run, exactly once")
            (is (str/includes? output (str "Category: " category)))
            (is (str/includes? output (str "Lingq Level: " level)))))))))

(deftest cli-rejects-unknown-classifier-before-processing
  (let [errors (atom [])]
    (with-redefs [parser/parse-article (fn [& _] (throw (ex-info "Unexpected parsing" {})))
                  openai/article-text->classification! (fn [& _] (throw (ex-info "Unexpected OpenAI call" {})))
                  typesafe/article-text->classification! (fn [& _] (throw (ex-info "Unexpected TypeSafe call" {})))
                  url/url (fn [& _] (throw (ex-info "Unexpected command dispatch" {})))]
      (with-redefs-fn {#'core/dispatch-options (assoc @#'core/dispatch-options
                                                      :error-fn #(swap! errors conj %))}
        #(core/-main "url" "https://example.com/article" "--classifier" "unknown"))
      (is (seq @errors))
      (is (some #(str/includes? (:msg %) "classifier") @errors)))))

(deftest cli-rejects-other-course-languages
  (let [errors (atom [])]
    (with-redefs [lingq/get-my-collections
                  (fn [& _] (throw (ex-info "Unexpected LingQ call" {})))]
      (with-redefs-fn {#'core/dispatch-options
                       (assoc @#'core/dispatch-options :error-fn #(swap! errors conj %))}
        #(core/-main "courses" "fr"))
      (is (seq @errors)))))

(deftest resolves-category-course-id
  (doseq [[category title] [["culture" "Culture"]
                            ["politics" "POLITICS"]
                            ["health" "hEaLtH"]
                            ["science-technology" "Science & Technology"]
                            ["education-careers" "Education & Careers"]]]
    (testing (str category " matches " title " without requiring a fallback")
      (is (= 123 (url/resolve-course-id category [{:id 123 :title title}]))))))

(deftest resolves-fallback-course-id
  (doseq [collections [[{:id 1 :title "Politics"}]
                       [{:id 1 :title "Culture"} {:id 2 :title "CULTURE"}]]]
    (is (= 456 (url/resolve-course-id
                "culture"
                (conj collections {:id 456 :title "Quick Imports"}))))))

(deftest rejects-missing-or-ambiguous-fallback
  (doseq [[collections fallback-count]
          [[[] 0]
           [[{:id 1 :title "Politics"}] 0]
           [[{:id 1 :title "Culture"} {:id 2 :title "CULTURE"}] 0]
           [[{:id 3 :title "Quick Imports"}
             {:id 4 :title "QUICK IMPORTS"}] 2]]]
    (try
      (url/resolve-course-id "culture" collections)
      (is false "Expected course resolution to fail")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Could not select a unique Quick Imports course" (ex-message e)))
        (is (= {:category "culture" :fallback-matches fallback-count}
               (ex-data e)))))))

(deftest url-course-selection-workflow
  (doseq [[label options expected-calls expected-id]
          [["classified category" {} [:collections :audio :upload] 123]
           ["category override" {:category "culture"} [:collections :audio :upload] 456]
           ["fallback" {:category "weather"} [:collections :audio :upload] 789]
           ["explicit course" {:course-id 999} [:audio :upload] 999]
           ["dry run" {:dry-run true} [] nil]]]
    (testing label
      (let [calls (atom [])]
        (with-redefs [parser/parse-article (fn [& _] {:title "Title" :text "本文"})
                      openai/article-text->classification!
                      (fn [_] {:category "science-technology" :jlpt-level "N3"})
                      typesafe/article-text->classification!
                      (fn [& _] (throw (AssertionError. "Unexpected TypeSafe call")))
                      lingq/get-my-collections
                      (fn [language]
                        (is (not (or (:course-id options) (:dry-run options)))
                            "Explicit courses and dry runs must skip collection lookup")
                        (is (= "ja" language))
                        (swap! calls conj :collections)
                        {:results [{:id 123 :title "Science & Technology"}
                                   {:id 456 :title "Culture"}
                                   {:id 789 :title "Quick Imports"}]})
                      tts/text-to-speech!
                      (fn [_ audio-options]
                        (is (= (or (:category options) "science-technology")
                               (:category audio-options)))
                        (swap! calls conj :audio)
                        :audio)
                      lingq/create-lesson
                      (fn [lesson]
                        (is (= expected-id (:course-id lesson)))
                        (is (= :audio (:audio lesson)))
                        (swap! calls conj :upload))]
          (with-out-str
            (#'url/create-lesson!
             (merge {:url "https://example.com/article" :classifier "openai"}
                    options)))
          (is (= expected-calls @calls)))))))

(deftest course-selection-failures-prevent-audio-and-upload
  (doseq [failure [:resolution :api]]
    (testing (name failure)
      (let [calls (atom [])
            api-error (ex-info "Collection lookup failed" {:status 503})]
        (with-redefs [parser/parse-article (fn [& _] {:title "Title" :text "本文"})
                      openai/article-text->classification!
                      (fn [_] {:category "culture" :jlpt-level "N3"})
                      typesafe/article-text->classification!
                      (fn [& _] (throw (AssertionError. "Unexpected TypeSafe call")))
                      lingq/get-my-collections
                      (fn [_]
                        (swap! calls conj :collections)
                        (if (= failure :api)
                          (throw api-error)
                          {:results []}))
                      tts/text-to-speech!
                      (fn [& _] (throw (AssertionError. "Unexpected audio generation")))
                      lingq/create-lesson
                      (fn [& _] (throw (AssertionError. "Unexpected upload")))]
          (with-out-str
            (try
              (#'url/create-lesson! {:url "https://example.com/article"
                                     :classifier "openai"})
              (is false "Expected course selection to fail")
              (catch clojure.lang.ExceptionInfo e
                (if (= failure :api)
                  (is (identical? api-error e))
                  (is (= {:category "culture" :fallback-matches 0}
                         (ex-data e)))))))
          (is (= [:collections] @calls)))))))
