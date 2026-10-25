(ns lingq-lesson.lessons-cmd-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [lingq-lesson.lessons-cmd :as lessons-cmd]
   [lingq-lesson.lingq :as lingq]))

(deftest title-column-formats-titles
  (doseq [[description text width expected]
          [["pads short titles" "News" 8 "News    "]
           ["keeps exact-width English titles" "News" 4 "News"]
           ["truncates English titles" "Morning News" 8 "Morning…"]
           ["keeps exact-width Japanese titles" "日本語" 6 "日本語"]
           ["truncates Japanese titles and pads the remaining column" "日本語記事" 6 "日本… "]
           ["fits mixed Japanese and English titles" "日本 News" 8 "日本 Ne…"]
           ["counts full-width Latin characters as two columns" "ＡＢＣ" 5 "ＡＢ…"]
           ["pads missing titles" nil 4 "    "]
           ["supports a one-column truncated title" "日本語" 1 "…"]
           ["supports a one-column exact title" "A" 1 "A"]]]
    (testing description
      (is (= expected (#'lessons-cmd/title-column text width))))))

(deftest lessons-aligns-columns-and-border
  (let [output (with-redefs [lingq/get-my-lessons
                             (fn [_]
                               [{:id 123 :title "日本語"
                                 :source {:url "https://example.com/ja"}}
                                {:id 456 :title "News"
                                 :source {:url "https://example.com/en"}}])]
                 (with-out-str
                   (lessons-cmd/lessons {:opts {:course-id 789}})))
        [header border japanese-row english-row] (str/split-lines output)]
    (is (= (apply str (repeat (count header) \-)) border))
    (is (= (str "ID           Title" (apply str (repeat 62 \space))
                "Source URL")
           header))
    (is (= (str "123          日本語" (apply str (repeat 61 \space))
                "https://example.com/ja")
           japanese-row))
    (is (= (str "456          News" (apply str (repeat 63 \space))
                "https://example.com/en")
           english-row))))
