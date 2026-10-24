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
                    tts/text-to-speech! (fn [_ opts]
                                          (is (= {:category "culture" :voice "onyx"} opts))
                                          (swap! calls conj :audio) :audio)
                    lingq/create-lesson (fn [lesson]
                                          (is (= (if override-level 4 3) (:level lesson)))
                                          (is (= :audio (:audio lesson)))
                                          (swap! calls conj :upload))]
        (let [output (with-out-str
                       (#'url/create-lesson! {:url "https://example.com/article"
                                              :image "https://example.com/cover"
                                              :classifier provider :dry-run dry-run
                                              :category "culture" :voice "onyx" :jlpt-level override-level}))]
          (is (= (cond-> (if override-level [] [provider])
                   (not dry-run) (into [:audio :upload]))
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
