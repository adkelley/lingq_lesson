(ns lingq-lesson.parser-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [lingq-lesson.parser :as parser]))

(def sample-article
  (let [markdown-content (str "# 記事の見出し\n\n"
                              "これは**太字**と_強調_を含む最初の文です。\n\n"
                              "![写真の説明](https://example.com/image.jpg)\n\n"
                              "[リンク付きの文](https://example.com/link)も本文として残します。\n\n"
                              "> 引用された文です。\n\n"
                              "- 箇条書きの一文です。\n"
                              "- もう一つの箇条書きです！\n\n"
                              "1. 番号付きリストの文です。\n\n"
                              "```html\n"
                              "<p>コードブロック内の文です。</p>\n"
                              "```\n\n"
                              "<div>HTMLタグ内の文です。</div>\n\n"
                              "<script>console.log(\"本文ではありません。\");</script>\n\n"
                              "最後の文です。")]
    (json/generate-string
     {"content" ""
      "contentMarkdown" markdown-content
      "title" "my title"
      "description" "my description"
      "domain" "example.com"
      "favicon" "/favicon.ico"
      "image" "https://example.com/media/2026/06/20260604-GYT1I00048-1.jpg?type=large"
      "language" "ja"
      "metaTags" [{"name" nil
                   "property" nil
                   "content" "IE=Edge"}
                  {"name" "viewport"
                   "property" nil
                   "content" "width=device-width"}
                  {"name" "robots"
                   "property" nil
                   "content" "max-image-preview:large"}
                  {"name" "yol:feature-name"
                   "property" nil
                   "content" "闇バイト"}
                  {"name" "description"
                   "property" nil
                   "content" "my description"}
                  {"name" nil
                   "property" "fb:app_id"
                   "content" "696165403843173"}
                  {"name" nil
                   "property" "og:site_name"
                   "content" "example"}
                  {"name" nil
                   "property" "og:type"
                   "content" "article"}
                  {"name" nil
                   "property" "og:title"
                   "content" "栃木強盗殺人、「主導役」は中国経由でカンボジア入国か…さらに「東南アジアの別の国へ出国」の情報も"}
                  {"name" nil
                   "property" "og:description"
                   "content" "my description"}
                  {"name" nil
                   "property" "og:image"
                   "content" "https://www.yomiuri.co.jp/media/2026/06/20260604-GYT1I00048-1.jpg?type=ogp"}
                  {"name" nil
                   "property" "og:url"
                   "content" "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"}
                  {"name" "twitter:site"
                   "property" nil
                   "content" "@Yomiuri_Online"}
                  {"name" "twitter:card"
                   "property" nil
                   "content" "summary_large_image"}
                  {"name" "twitter:title"
                   "property" nil
                   "content" "栃木強盗殺人、「主導役」は中国経由でカンボジア入国か…さらに「東南アジアの別の国へ出国」の情報も"}
                  {"name" "twitter:description"
                   "property" nil
                   "content" "my description"}
                  {"name" "twitter:url"
                   "property" nil
                   "content" "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"}
                  {"name" nil
                   "property" "article:published_time"
                   "content" "2026-06-04T05:00:00+09:00"}
                  {"name" nil
                   "property" "article:modified_time"
                   "content" "2026-06-04T07:19:58+09:00"}
                  {"name" "theme-color"
                   "property" nil
                   "content" "#4B0082"}]
      "parseTime" 154
      "published" "2026-06-04T05:00:00+09:00"
      "author" "読売新聞オンライン"
      "site" "読売新聞オンライン"
      "schemaOrgData" [[{"@context" "http://schema.org/"
                         "@type" "NewsArticle"
                         "mainEntityOfPage" {"@type" "WebPage"
                                             "@id" "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"}
                         "url" "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"
                         "headline" "栃木強盗殺人、「主導役」は中国経由でカンボジア入国か…さらに「東南アジアの別の国へ出国」の情報も"
                         "datePublished" "2026-06-04T05:00:00+09:00"
                         "dateModified" "2026-06-04T07:19:58+09:00"
                         "publisher" {"@type" "Organization"
                                      "@id" "https://www.yomiuri.co.jp"
                                      "name" "読売新聞オンライン"
                                      "logo" {"@type" "ImageObject"
                                              "url" "https://www.yomiuri.co.jp/dc-yollogo_amp.png"
                                              "width" 426
                                              "height" 60}}
                         "image" "https://www.yomiuri.co.jp/media/2026/06/20260604-GYT1I00048-1.jpg?type=ogp"
                         "articleSection" "社会"
                         "description" "my description"
                         "author" {"@type" "Organization"
                                   "name" "読売新聞オンライン"}
                         "isAccessibleForFree" true
                         "isPartOf" {"@type" ["NewsArticle" "Product"]
                                     "name" "栃木強盗殺人、「主導役」は中国経由でカンボジア入国か…さらに「東南アジアの別の国へ出国」の情報も"
                                     "productID" "yomiuri.co.jp:showcase"}}
                        {"@context" "http://schema.org/"
                         "@type" "BreadcrumbList"
                         "itemListElement" [[{"@type" "ListItem"
                                              "position" 1
                                              "name" "社会"
                                              "item" "https://www.yomiuri.co.jp/national"}]]}]]
      "wordCount" 717})))

(def parsed-sample-article
  (json/parse-string sample-article true))

(deftest parse-defuddle-json-keywordizes-article-data
  (let [parsed (#'parser/parse-defuddle-json sample-article)]
    (is (= "my title" (:title parsed)))
    (is (= "my description" (:description parsed)))
    (is (= "example" (get-in parsed [:metaTags 6 :content])))
    (is (= "NewsArticle"
           (get-in parsed [:schemaOrgData 0 0 (keyword "@type")])))))

(deftest markdown->text-strips-markdown-and-keeps-readable-sentences
  (let [text (#'parser/markdown->text (:contentMarkdown parsed-sample-article))
        lines (clojure.string/split-lines text)]
    (is (= ["これは太字と強調を含む最初の文です。"
            "リンク付きの文も本文として残します。"
            "引用された文です。"
            "箇条書きの一文です。"
            "もう一つの箇条書きです！"
            "番号付きリストの文です。"
            "コードブロック内の文です。"
            "HTMLタグ内の文です。"
            "最後の文です。"]
           lines))
    (is (not (string/includes? text "#")))
    (is (not (string/includes? text "**")))
    (is (not (string/includes? text "_")))
    (is (not (string/includes? text "![")))
    (is (not (string/includes? text "https://example.com")))
    (is (not (string/includes? text "<div>")))
    (is (not (string/includes? text "console.log")))))

(deftest meta-content-by-property-finds-first-matching-content
  (is (= "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"
         (#'parser/meta-content-by-property (:metaTags parsed-sample-article) "og:url")))
  (is (= "article"
         (#'parser/meta-content-by-property (:metaTags parsed-sample-article) "og:type")))
  (is (nil? (#'parser/meta-content-by-property (:metaTags parsed-sample-article) "missing"))))

(deftest extract-tags-uses-open-graph-site-and-type
  (is (= ["example" "article"]
         (#'parser/extract-tags parsed-sample-article))))

(deftest defuddle->article-maps-parser-output-to-lesson-input
  (with-redefs [parser/download-image! (fn [url]
                                         (is (= "https://example.com/media/2026/06/20260604-GYT1I00048-1.jpg?type=large"
                                                url))
                                         (.getBytes "image-bytes"))]
    (let [article (#'parser/defuddle->article parsed-sample-article)]
      (is (= "my title" (:title article)))
      (is (= "my description" (:description article)))
      (is (= ["example" "article"] (:tags article)))
      (is (= "https://www.yomiuri.co.jp/national/20260604-GYT1T00046/"
             (:original-url article)))
      (is (= "これは太字と強調を含む最初の文です。"
             (first (string/split-lines (:text article)))))
      (is (= "image-bytes" (String. (:image article)))))))
