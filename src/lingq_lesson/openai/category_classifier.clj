(ns lingq-lesson.openai.category-classifier)

(def category-question
  {:type "choice"
   :name "category"
   :instructions "Determine the article's primary subject category. The article may be written in any language. Choose based on its central subject and editorial focus, rather than incidental mentions or its format (report, editorial, or column). Prefer a specific subject category over regional or international when the subject is the main focus."
   :choices [{:value "society" :description "Crime, courts, accidents, social issues, and public safety."}
             {:value "politics" :description "Government, elections, legislation, and public policy."}
             {:value "business" :description "The economy, companies, commerce, markets, finance, and employment."}
             {:value "sports" :description "Athletic competition, athletes, teams, and sporting events."}
             {:value "international" :description "Foreign affairs, diplomacy, and overseas developments whose international context is central."}
             {:value "regional" :description "Local communities and regional affairs without a more specific subject category."}
             {:value "science-technology" :description "Scientific research, space exploration, computing, IT, engineering, and technological development."}
             {:value "culture" :description "Entertainment, film, television, music, books, arts, and cultural heritage."}
             {:value "lifestyle" :description "Food, travel, relationships, hobbies, fashion, and everyday living."}
             {:value "education-careers" :description "Education, schools, examinations, admissions, career preparation, and job hunting."}
             {:value "health" :description "Medicine, healthcare, disease, treatment, and physical or mental wellbeing."}
             {:value "weather" :description "Weather, forecasts, and meteorological events."}
             {:value "other" :description "Content that does not fit any listed category or lacks enough information to classify."}]})
