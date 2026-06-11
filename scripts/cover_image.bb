#!/usr/bin/env bb

(require
 '[babashka.fs :as fs]
 '[babashka.cli :as cli]
 '[babashka.process :refer [shell]]
 '[clojure.java.io :as io]
 '[cheshire.core :as json]
 '[hato.client :as hc])

(defn exit-with-error [message]
  (binding [*out* *err*]
    (println message))
  (System/exit 1))

(defn ensure-magick! []
  (try
    (shell {:out :string :err :string} "magick" "-version")
    (catch Exception _
      (exit-with-error "ImageMagick is required. Install it with: brew install imagemagick"))))

(defn download-image! [url output-path]
  (let [response (hc/get url {:as :byte-array
                              :throw-exceptions false})
        {:keys [status body]} response]
    (when-not (<= 200 status 299)
      (throw (ex-info "Failed to download image"
                      {:status status
                       :url url})))
    (with-open [out (io/output-stream output-path)]
      (.write out ^bytes body))))

(defn first-image-url [json-file]
  (let [content (slurp json-file)
        images (json/parse-string content)]
    (get (first images) "url")))

;;
;; CLI
(def supported-image-formats #{"jpg", "jpeg"})

(def usage "Usage: cover_image.bb <image_json_file> [--cover <cover_image.jpg>]")

(def cli-spec
  {:restrict false
   :spec
   {:cover   {:alias :c
              :desc "Cover image file path (JPG or JPEG)"
              :validate (fn [file-path] (contains? supported-image-formats (fs/extension file-path)))
              :require false}
    :help    {:alias :h
              :coerce :boolean
              :require false
              :desc "Show help"}}

   :error-fn
   (fn [{:keys [spec type cause msg option] :as data}]
     (binding [*out* *err*]
       (when (= :org.babashka/cli type)
         (case cause
           :require
           (println
            (format "Missing required argument: %s - %s"
                    option
                    (:desc spec)))

           :validate
           (println msg)

           (do
             (println "Unexpected error:")
             (prn data)))))
     (System/exit 1))})

(defn- show-help [spec]
  (str usage
       "\n\n"
       "Downloads the first image URL from extracted image JSON for LingQ upload.\n"
       "\n"
       "Options:\n"
       (cli/format-opts (merge spec {:order [:cover :help]}))
       "\n\n"
       "Environment:\n"
       "ImageMagick                 Required.\n\n"))

(defn- help?
  [args]
  (some #{"--help" "-h"} args))

(when (help? *command-line-args*)
  (println (show-help cli-spec))
  (System/exit 0))

(let [{:keys [args, opts]} (cli/parse-args *command-line-args* cli-spec)
      input-file (first args)]

  (when-not (= 1 (count args))
    (exit-with-error usage))

  (when-not (fs/exists? input-file)
    (exit-with-error "Input file does not exist"))

  (let [image-url (first-image-url input-file)
        cover-image (or (:cover opts)
                        "cover_image.jpg")]
    (download-image! image-url cover-image)))
