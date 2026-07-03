(ns lingq-lesson.audio-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [lingq-lesson.audio :as audio]
   [lingq-lesson.audio-instructions :as audio-instructions]
   [lingq-lesson.openai :as openai]))

(deftest text-to-speech-posts-encoded-payload
  (let [request (atom nil)
        audio-bytes (.getBytes "audio-bytes" "UTF-8")]
    (with-redefs [openai/api-key (constantly "test-key")
                  openai/require-api-key (constantly nil)
                  hc/post (fn [url opts]
                            (reset! request {:url url
                                             :opts opts})
                            {:status 200
                             :headers {}
                             :body audio-bytes})]
      (is (= audio-bytes
             (audio/text-to-speech! "lesson text" {:voice "nova"
                                                   :vibe "lifestyle"})))
      (is (= openai/speech-url (:url @request)))
      (is (= {:headers {"authorization" "Bearer test-key"
                        "content-type" "application/json"}
              :as :byte-array
              :throw-exceptions false}
             (dissoc (:opts @request) :body)))
      (is (= {:model "gpt-4o-mini-tts"
              :input "lesson text"
              :voice "nova"
              :response_format "mp3"
              :instructions audio-instructions/lifestyle}
             (json/decode (get-in @request [:opts :body]) keyword))))))

(deftest text-to-speech-uses-default-options
  (let [payload (atom nil)]
    (with-redefs [openai/api-key (constantly "test-key")
                  openai/require-api-key (constantly nil)
                  hc/post (fn [_ opts]
                            (reset! payload (json/decode (:body opts) keyword))
                            {:status 200
                             :headers {}
                             :body (.getBytes "audio-bytes" "UTF-8")})]
      (audio/text-to-speech! "lesson text" {})
      (is (= "alloy" (:voice @payload)))
      (is (= "mp3" (:response_format @payload)))
      (is (= audio-instructions/newscaster (:instructions @payload))))))

(deftest text-to-speech-throws-response-errors
  (with-redefs [openai/api-key (constantly "test-key")
                openai/require-api-key (constantly nil)
                hc/post (fn [_ _]
                          {:status 400
                           :headers {}
                           :body (.getBytes "{\"error\":{\"message\":\"bad request\"}}" "UTF-8")})]
    (try
      (audio/text-to-speech! "lesson text" {})
      (is false "Expected OpenAI response error")
      (catch clojure.lang.ExceptionInfo e
        (is (= "OpenAI returned status 400. bad request"
               (ex-message e)))
        (is (= "bad request"
               (get-in (ex-data e) [:body :error :message])))))))
