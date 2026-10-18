(ns lingq-lesson.lingq-test
  (:require
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [lingq-lesson.lingq :as lingq]))

(defn- part-content [multipart name]
  (:content (first (filter #(= name (:name %)) multipart))))

(deftest multipart-parts-includes-original-url
  (let [multipart (#'lingq/multipart-parts {:title "Title"
                                            :text "Text"
                                            :status "private"
                                            :image "image-bytes"
                                            :level 3
                                            :tags ["news"]
                                            :description "Description"
                                            :original-url "https://example.com/article"})]
    (is (= "https://example.com/article"
           (part-content multipart "originalUrl")))))

(deftest get-my-collections-fetches-and-parses-collections
  (let [request (atom nil)
        response-body "[{\"id\":123456,\"title\":\"Japanese Politics\"}]"]
    (with-redefs-fn {#'lingq/request-headers (constantly {"authorization" "Token test-key"})
                     #'hc/get (fn [url opts]
                                (reset! request {:url url :opts opts})
                                {:status 200
                                 :headers {}
                                 :body response-body})}
      (fn []
        (is (= [{:id 123456 :title "Japanese Politics"}]
               (lingq/get-my-collections "ja")))
        (is (= "https://www.lingq.com/api/v3/ja/collections/my/"
               (:url @request)))
        (is (= {"authorization" "Token test-key"}
               (get-in @request [:opts :headers])))
        (is (= false (get-in @request [:opts :throw-exceptions])))))))

(deftest get-my-collections-throws-on-api-error
  (with-redefs-fn {#'lingq/request-headers (constantly {"authorization" "Token test-key"})
                   #'hc/get (fn [_ _]
                              {:status 401
                               :headers {}
                               :body "{\"detail\":\"Invalid token.\"}"})}
    (fn []
      (try
        (lingq/get-my-collections "ja")
        (is false "Expected LingQ response error")
        (catch clojure.lang.ExceptionInfo e
          (is (= 401 (get-in (ex-data e) [:status])))
          (is (= {:detail "Invalid token."}
                 (get-in (ex-data e) [:body]))))))))
