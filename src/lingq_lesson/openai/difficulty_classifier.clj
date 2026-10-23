(ns lingq-lesson.openai.difficulty-classifier)

(def choice->difficulty
  {"n5"       {:jlpt-level "N5" :lingq-level 1}
   "n4"       {:jlpt-level "N4" :lingq-level 2}
   "n3"       {:jlpt-level "N3" :lingq-level 3}
   "n2"       {:jlpt-level "N2" :lingq-level 4}
   "n1"       {:jlpt-level "N1" :lingq-level 5}
   "above-n1" {:jlpt-level "Above N1" :lingq-level 6}})

(def difficulty-question
  {:type "choice"
   :name "difficulty"
   :instructions
   (str
    "Estimate the overall Japanese reading proficiency needed to "
    "understand this article comfortably. "
    "Consider vocabulary frequency and specialized terminology, "
    "grammar and implicit meaning, kanji difficulty and density, "
    "sentence length and nested clauses, and required domain knowledge. "
    "Judge the article as a whole rather than its hardest isolated word "
    "or sentence. Proper nouns and a technical subject alone do not "
    "make an article advanced. Select the best-fitting level.")
   :choices [{:value "n5"
              :description
              (str
               "Basic Japanese: familiar everyday words, elementary grammar, "
               "common basic kanji, and short, simple sentences. Meaning is "
               "explicit and requires little inference or background knowledge.")}

             {:value "n4"
              :description
              (str
               "Elementary Japanese: everyday topics with common vocabulary "
               "and kanji, basic connected sentences, and straightforward "
               "explanations or narratives. Some simple subordinate clauses.")}

             {:value "n3"
              :description
              (str
               "Intermediate Japanese: everyday topics and accessible reporting "
               "with some less familiar vocabulary and kanji. Moderately complex "
               "sentences and intermediate grammar; some inference is needed, "
               "but little specialized knowledge.")}

             {:value "n2"
              :description
              (str
               "Upper-intermediate Japanese: general newspaper and magazine "
               "articles, commentary, and abstract topics. Broad vocabulary "
               "and kanji, complex sentences, and advanced grammar. Understanding "
               "may require following arguments and implied relationships.")}

             {:value "n1"
              :description
              (str
               "Advanced Japanese: complex editorials, essays, and sophisticated "
               "general-audience prose. Extensive vocabulary and kanji, nuanced "
               "grammar, dense or nested sentences, and substantial inference "
               "about arguments, tone, and implicit meaning.")}

             {:value "above-n1"
              :description
              (str
               "Significantly beyond typical N1 reading difficulty: sustained "
               "specialist vocabulary, unusually difficult syntax, or highly "
               "sophisticated literary language. Understanding requires expert "
               "domain knowledge or exceptional linguistic proficiency, as in "
               "demanding academic, legal, technical, or literary texts. "
               "Do not select this merely because the article covers such a topic.")}]})
