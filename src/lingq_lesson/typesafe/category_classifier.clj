(ns lingq-lesson.typesafe.category-classifier)

(def category-question
  {:type "choice"
   :instructions "Determine the article's primary subject category. The article may be written in any language. Choose based on its central subject and editorial focus, rather than incidental mentions or its format (report, editorial, or column). Prefer a specific subject category over regional or international when the subject is the main focus."
   :criteria {:society "Crime, courts, accidents, social issues, and public safety."
              :politics "Government, elections, legislation, and public policy."
              :business "The economy, companies, commerce, markets, finance, and employment."
              :sports "Athletic competition, athletes, teams, and sporting events."
              :international "Foreign affairs, diplomacy, and overseas developments whose international context is central."
              :regional "Local communities and regional affairs without a more specific subject category."
              :science-technology "Scientific research, space exploration, computing, IT, engineering, and technological development."
              :culture "Entertainment, film, television, music, books, arts, and cultural heritage."
              :lifestyle "Food, travel, relationships, hobbies, fashion, and everyday living."
              :education-careers "Education, schools, examinations, admissions, career preparation, and job hunting."
              :health "Medicine, healthcare, disease, treatment, and physical or mental wellbeing."
              :weather "Weather, forecasts, and meteorological events."
              :other "Content that does not fit any listed category or lacks enough information to classify."}})
