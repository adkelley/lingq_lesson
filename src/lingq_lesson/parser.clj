(ns lingq-lesson.parser
  (:require
   [babashka.process :refer [shell]]))

(defn defuddle-exists? []
  (zero? (:exit (shell {:out :string :err :string :continue true}
                        "npx" "defuddle" "--version"))))

(defn parse-article [url]
  (when-not (defuddle-exists?)
    (throw (ex-info "defuddle not found" {})))

  (let [res (shell {:out :string :err :string :continue true}
                   "npx" "defuddle" "parse" url "--json")
        exit (:exit res 0)
        out  (:out res "")
        err  (:err res "")]
    (when-not (zero? exit)
      (throw (ex-info "Unable to parse article."
                      {:exit exit
                       :err err})))
    out))
