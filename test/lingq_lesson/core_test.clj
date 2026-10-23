(ns lingq-lesson.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [lingq-lesson.url-cmd :as url]
            [lingq-lesson.core :as core]
            [lingq-lesson.parser :as parser]
            [lingq-lesson.openai.article-classifier :as openai]
            [lingq-lesson.typesafe.article-classifier :as typesafe]
            [lingq-lesson.openai.audio :as audio]
            [lingq-lesson.lingq :as lingq]))

(deftest classifier-dispatch-category-and-overrides
  (doseq [provider ["openai" "typesafe"] dry-run [true false]]
    (let [calls (atom [])
          classify (fn [text]
                     (is (= "本文" text))
                     {:category "science-technology" :difficulty {:lingq-level 3}})]
      (with-redefs [parser/parse-article (fn [_ image options]
                                           (is (= "https://example.com/cover" image))
                                           (is (= (not dry-run) (:download-image? options)))
                                           {:title "Title" :text "本文"})
                    openai/article-text->classification! (fn [text] (swap! calls conj "openai") (classify text))
                    typesafe/article-text->classification! (fn [text] (swap! calls conj "typesafe") (classify text))
                    audio/text-to-speech! (fn [_ opts]
                                            (is (= {:category "culture" :voice "onyx"} opts))
                                            (swap! calls conj :audio) :audio)
                    lingq/create-lesson (fn [lesson]
                                          (is (= 4 (:level lesson)))
                                          (is (= :audio (:audio lesson)))
                                          (swap! calls conj :upload))]
        (let [output (with-out-str
                       (#'url/create-lesson! {:url "https://example.com/article"
                                              :image "https://example.com/cover"
                                              :classifier provider :dry-run dry-run
                                              :category "culture" :voice "onyx" :lingq-level 4}))]
          (is (= (if dry-run [provider] [provider :audio :upload]) @calls))
          (when dry-run
            (is (str/includes? output "Category: culture"))
            (is (str/includes? output "Lingq Level: 4"))))))))

(deftest recommended-voices
  (is (= "alloy" (#'url/resolve-voice "science-technology")))
  (is (= "nova" (#'url/resolve-voice "culture"))))

(deftest cli-selects-only-the-requested-classifier
  (doseq [[flags expected category level]
          [[[] "typesafe" "health" 4]
           [["--classifier" "typesafe"] "typesafe" "health" 4]
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
                               {:category "science-technology" :difficulty {:lingq-level 3}})
                      typesafe/article-text->classification!
                      (partial classify "typesafe"
                               {:category "health" :difficulty {:lingq-level 4}})
                      audio/text-to-speech! (fn [& _] (throw (ex-info "Unexpected audio" {})))
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
