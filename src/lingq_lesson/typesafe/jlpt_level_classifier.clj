(ns lingq-lesson.typesafe.jlpt-level-classifier)

(def jlpt-level-question
  {:type "choice"
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
   :criteria
   {:N5
    (str
     "Basic Japanese: familiar everyday words, elementary grammar, "
     "common basic kanji, and short, simple sentences. Meaning is "
     "explicit and requires little inference or background knowledge.")

    :N4
    (str
     "Elementary Japanese: everyday topics with common vocabulary "
     "and kanji, basic connected sentences, and straightforward "
     "explanations or narratives. Some simple subordinate clauses.")

    :N3
    (str
     "Intermediate Japanese: everyday topics and accessible reporting "
     "with some less familiar vocabulary and kanji. Moderately complex "
     "sentences and intermediate grammar; some inference is needed, "
     "but little specialized knowledge.")

    :N2
    (str
     "Upper-intermediate Japanese: general newspaper and magazine "
     "articles, commentary, and abstract topics. Broad vocabulary "
     "and kanji, complex sentences, and advanced grammar. Understanding "
     "may require following arguments and implied relationships.")

    :N1
    (str
     "Advanced Japanese: complex editorials, essays, and sophisticated "
     "general-audience prose. Extensive vocabulary and kanji, nuanced "
     "grammar, dense or nested sentences, and substantial inference "
     "about arguments, tone, and implicit meaning.")

    :above-n1
    (str
     "Significantly beyond typical N1 reading difficulty: sustained "
     "specialist vocabulary, unusually difficult syntax, or highly "
     "sophisticated literary language. Understanding requires expert "
     "domain knowledge or exceptional linguistic proficiency, as in "
     "demanding academic, legal, technical, or literary texts. "
     "Do not select this merely because the article covers such a topic.")}})
