(ns lingq-lesson.audio-instructions)

(def business
  (str
   "Voice: Calm, authoritative, and professional, with a measured and confident cadence. "
   "The delivery should sound like an experienced business journalist or financial correspondent"
   "explaining important developments to an informed audience."
   "\n"
   "Phrasing: Sentences are clear, concise, and logically structured, with natural emphasis "
   "on company names, executives, financial figures, market indices, economic indicators, and "
   "key conclusions. Slow slightly when presenting numbers, statistics, dates, and specialized business or "
   "financial terminology to ensure accuracy and comprehension."
   "\n"
   "Punctuation: Use deliberate pauses at commas and sentence boundaries, with slightly longer pauses "
   "after important earnings results, market movements, major corporate announcements or significant "
   "economic developments. Pauses should give listeners time to absorb information rather than create "
   "drama or suspense."
   "\n"
   "Tone: Objective, credible, and analytical. Maintain a composed and thoughtful demeanor, "
   "conveying the significance of business and financial developments. The narration should inspire "
   "confidence and understanding without sounding alarmist, promotional, or overly enthusiastic."
   "Complex topics should feel accessible and informative, as thougn guiding listeners through the "
   "implications of the news."))

(def newscaster
  (str
   "Voice: Clear, confident, and professional, with a well-supported mid-to-deep "
   "register. Speech is articulate and steady, conveying credibility and authority."
   "\n"
   "Phrasing: Sentences flow smoothly and logically, with emphasis placed on key facts, "
   "names, locations, and developments. Information is presented efficiently and without "
   "unnecessary dramatization."
   "\n"
   "Punctuation: Natural pauses at commas and sentence boundaries. Brief pauses separate "
   "topics and transitions. Avoid excessive hesitation, ellipses, or dramatic stops."
   "\n"
   "Tone: Objective, composed, and informative. Maintain a calm, measured delivery that "
   "prioritizes clarity, accuracy, and listener comprehension while remaining engaging and attentive."))

(def sportscaster
  (str
   "Voice: Energetic, confident, and expressive, with a warm, conversational quality."
   "The pace is brisk but controlled, capable of rising in intensity during exciting moments and "
   "settling during analysis or statistics."
   "\n"
   "Phrasing: Sentences are dynamic and forward-moving, with natural emphasis on players, "
   "teams, scores, and pivotal moments. Key names and action verbs are highlighted to create "
   "momentum and excitement."
   "\n"
   "Punctuation: Use natural pauses to separate facts and transitions. Slightly longer pauses "
   "can follow major plays, scoring moments, or surprising developments to let important "
   "information land. Exclamation should be conveyed through vocal energy rather than excessive "
   "punctuation."
   "\n"
   "Tone: Enthusiastic, engaging, and passionate about the sport while remaining informative "
   "and credible. Celebrate exciting moments and dramatic turns naturally, but avoid sounding "
   "overly theatrical or biased unless the text itself clearly calls for it."))

(def lifestyle
  (str
   "Voice: Warm, polished, and sophisticated, with a smooth, conversational cadence. "
   "The delivery should feel personable and refined, as though an experienced magazine editor "
   "is guiding the listener through the story. "
   "\n"
   "Phrasing: Sentences flow naturally with gentle emphasis on descriptive language, people, places, "
   "and memorable details. Allow evocative phrases and colorful imagery a little extra space to breathe. "
   "\n"
   "Punctuation: Use moderate pauses at commas and sentence boundaries, with slightly longer pauses "
   "between sections or changes in topic. The pacing should feel relaxed and unhurried, never rushed or overly dramatic. "
   "\n"
   "Tone: Curious, thoughtful, and engaging. The narration should convey appreciation for the subject "
   "matter—whether fashion, culture, travel, or lifestyle—while remaining tasteful and understated. "
   "It should invite the listener into the story rather than simply present facts."))

(def technology
  (str
   "Voice: Clear, intelligent, and confident, with a calm, conversational cadence. The delivery "
   "should sound knowledgeable and approachable, as though an experienced technology journalist is "
   "explaining important developments to an informed audience."
   "\n"
   "Phrasing: Sentences flow smoothly and logically, with natural emphasis on technical terms, company names, "
   "product names, and key concepts. Slow slightly for English loanwords, acronyms, version numbers, and unfamiliar "
   "terminology to aid comprehension. Complex ideas should be presented in a measured, easy-to-follow manner."
   "\n"
   "Punctuation: Use natural pauses at commas and sentence boundaries, with slightly longer pauses before introducing "
   "major announcements, new technologies, or significant implications. Pauses should support comprehension rather than "
   "create drama or suspense."
   "\n"
   "Tone: Thoughtful, curious, and forward-looking. Maintain a professional and credible demeanor while conveying "
   "genuine interest in innovation and technology. The narration should make complex subjects feel accessible and "
   "intellectually stimulating without sounding promotional, sensational, or overly excited."))

(def  supported-vibes #{"business" "news" "sports" "lifestyle" "technology"})

(def instructions
  {:business   business
   :news      newscaster
   :sports    sportscaster
   :lifestyle lifestyle
   :technology technology})

(defn for-vibe [vibe]
  (get instructions (keyword vibe) newscaster))

(defn supported-vibe? [vibe]
  (contains? supported-vibes vibe))
