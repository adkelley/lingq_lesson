(ns lingq-lesson.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [lingq-lesson.openai.audio :as audio]
   [lingq-lesson.openai.difficulty-classifier :as difficulty-classifier]
   [lingq-lesson.lingq :as lingq]
   [lingq-lesson.parser :as parser]
   [lingq-lesson.openai.style-classifier :as style-classifier]
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

(deftest dry-run-assesses-article-and-honors-overrides
  (doseq [overrides [{} {:vibe "sports"} {:lingq-level 4}
                     {:vibe "sports" :lingq-level 4}]
          silent [false true]]
    (testing (str "overrides " overrides ", silent " silent)
      (let [calls (atom [])
            article {:title "Sample article" :text "本文です。"}
            opts (merge {:url "https://example.com/article"
                         :dry-run true :silent silent}
                        overrides)]
        (with-redefs [parser/parse-article
                      (fn [url image-url options]
                        (swap! calls conj [:parse url image-url options])
                        article)
                      style-classifier/article-text->style!
                      (fn [text verbose]
                        (swap! calls conj [:style text verbose])
                        (when verbose (println "Detailed style assessment"))
                        {:vibe "news"})
                      difficulty-classifier/article-text->difficulty!
                      (fn [text verbose]
                        (swap! calls conj [:difficulty text verbose])
                        (when verbose (println "Detailed difficulty assessment"))
                        {:lingq-level 3})
                      audio/text-to-speech!
                      (fn [& _] (throw (ex-info "Unexpected audio generation" {})))
                      lingq/create-lesson
                      (fn [& _] (throw (ex-info "Unexpected LingQ upload" {})))]
          (let [output (with-out-str (#'url-cmd/create-lesson! opts))]
            (is (= [:parse (:url opts) nil {:download-image? false}]
                   (first @calls)))
            (is (= (if (:vibe overrides) [] [[:style (:text article) (not silent)]])
                   (filterv #(= :style (first %)) @calls)))
            (is (= (if (:lingq-level overrides) [] [[:difficulty (:text article) (not silent)]])
                   (filterv #(= :difficulty (first %)) @calls)))
            (is (str/includes? output "Dry run complete"))
            (is (str/includes? output "Title: Sample article"))
            (is (str/includes? output (str "Style: " (or (:vibe overrides) "news"))))
            (is (str/includes? output (str "Lingq Level: " (or (:lingq-level overrides) 3))))
            (when silent
              (is (not (str/includes? output "Detailed style assessment"))))
            (when silent
              (is (not (str/includes? output "Detailed difficulty assessment"))))))))))

(deftest normal-creation-still-generates-audio-and-uploads
  (let [calls (atom [])
        article {:title "Sample article" :text "本文です。" :image :cover}]
    (with-redefs [parser/parse-article
                  (fn [url image-url options]
                    (swap! calls conj [:parse url image-url options])
                    article)
                  style-classifier/article-text->style!
                  (fn [text verbose]
                    (swap! calls conj [:style text verbose])
                    {:vibe "news"})
                  difficulty-classifier/article-text->difficulty!
                  (fn [text verbose]
                    (swap! calls conj [:difficulty text verbose])
                    {:lingq-level 3})
                  audio/text-to-speech!
                  (fn [text options]
                    (swap! calls conj [:audio text options])
                    :generated-audio)
                  lingq/create-lesson
                  (fn [lesson]
                    (swap! calls conj [:upload lesson]))]
      (with-out-str
        (#'url-cmd/create-lesson! {:url "https://example.com/article"
                                   :silent true :course-id "123"}))
      (is (= [:parse "https://example.com/article" nil {:download-image? true}]
             (first @calls)))
      (is (= [[:style (:text article) false]]
             (filterv #(= :style (first %)) @calls)))
      (is (= [[:difficulty (:text article) false]]
             (filterv #(= :difficulty (first %)) @calls)))
      (is (= [[:audio "Sample article\n\n本文です。" {:voice "alloy" :vibe "news"}]]
             (filterv #(= :audio (first %)) @calls)))
      (is (= [[:upload (merge article {:status "private" :level 3
                                       :audio :generated-audio
                                       :original-url "https://example.com/article"
                                       :course-id "123"})]]
             (filterv #(= :upload (first %)) @calls))))))
