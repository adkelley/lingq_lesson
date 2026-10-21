# LingQ Lesson

Create a LingQ lesson from an article URL.

The app fetches article content with Defuddle, extracts readable lesson text,
downloads the article image, estimates the article's JLPT difficulty with
OpenAI, maps that estimate to a LingQ lesson level, generates OpenAI
text-to-speech audio, and posts a private Japanese lesson to LingQ.

## Usage

List the courses available in a LingQ language:

```sh
bb lingq courses ja
```

The command prints each course's ID and title. Use the ID when selecting a
course for a lesson upload.

The optional `--course-id` flag assigns a lesson to a selected course:

```sh
bb lingq url https://example.com/article --course-id 123456
```

Example output:

```text
ID           Title
------------------------------------------------------------
123456       Japanese Politics
234567       Japanese News
```

Create a lesson from an article URL:

```sh
bb lingq url https://example.com/article
```

The `url` command takes the article URL as its required positional argument.
Optional flags may follow it:

```sh
bb lingq url https://example.com/article \
  --voice alloy \
  --vibe news \
  --course-id 123456 \
  --lingq-level 4 \
  --silent
```

On macOS, you can read the article URL from the clipboard and select the first
course whose title contains `Politics`:

```sh
bb lingq url "$(pbpaste)" \
  --course-id "$(bb lingq courses ja | awk '/Politics/ {print $1; exit}')"
```

Use `--lingq-level` with an integer from 1 through 6 to set the lesson difficulty
directly and skip automatic JLPT difficulty assessment. For example,
`--lingq-level 4` selects the level mapped to JLPT N2. Audio generation and
upload still run normally. Omit the option to estimate difficulty automatically.

Use `--dry-run` to fetch an article and print its title, style, and LingQ level
without creating a lesson:

```sh
bb lingq url https://example.com/article --dry-run
```

Dry runs skip cover-image downloads, audio generation, and all LingQ calls.
OpenAI still assesses style and difficulty unless their respective overrides
are supplied: `--vibe` skips style classification, and `--lingq-level` skips
difficulty assessment. The summary displays the effective values, including
any overrides. Use `--silent` to hide detailed assessments while keeping the
summary visible.

Use `--silent` to suppress the detailed style-classification and JLPT analysis
output. Pipeline progress messages remain visible so you can still follow the
lesson creation process.

Supported voices: `alloy` (male), `echo` (male), `onyx` (male), `nova` (female), `shimmer` (female).

Supported vibes: `business`, `crime`, `entertainment`, `news`, `politics`, `sports`, `lifestyle`, `technology`.

OpenAI intentionally describes the TTS voices in terms of their character and sound, 
not as explicitly male or female. That said, most listeners perceive them roughly as follows:

Voice | Common Perception | Characteristics
--- | --- | ---
alloy | Male-leaning / androgynous | Neutral, professional, versatile
onyx | Male | Deep, authoritative, documentary-style
echo | Male | Energetic, conversational, expressive
nova | Female | Warm, friendly, approachable
shimmer | Female | Soft, polished, refined

The recommended vibe/voice pairs are:

Vibe | Voice
--- | ---
`business` | `onyx`
`news` | `alloy`
`politics` | `onyx`
`sports` | `echo`
`lifestyle` | `nova`
`technology` | `alloy`

If neither `--vibe` nor `--voice` is specified, the app classifies the article
style with OpenAI and chooses a recommended vibe/voice pair automatically.

If `--vibe` is specified, automatic style classification is skipped. 
If `--voice` is unspecified, then a voice is selected based on the vibe. Any missing value falls back to the default: `news` for vibe and
`alloy` for voice.

Examples:

```sh
# Auto-classify article style and choose voice.
bb lingq url https://example.com/article

# Force only the vibe; voice falls back to alloy.
bb lingq url https://example.com/article --vibe technology

# Force only the voice; vibe is inferred from the article style.
bb lingq url https://example.com/article --voice nova

# Force both values.
bb lingq url https://example.com/article --vibe lifestyle --voice nova

# Hide detailed analysis while retaining pipeline progress messages.
bb lingq url https://example.com/article --silent

# Override difficulty and skip the JLPT predictor.
bb lingq url https://example.com/article --lingq-level 4

# Assess style and difficulty without generating audio or uploading.
bb lingq url https://example.com/article --dry-run --silent

# Preview supplied values without either OpenAI assessment.
bb lingq url https://example.com/article --dry-run --vibe news --lingq-level 4
```

Print general or command-specific help:

```sh
bb lingq --help
bb lingq url --help
```

## Requirements

Install Babashka and the Clojure CLI, then install Node dependencies:

```sh
npm install
```

The app shells out to the Defuddle CLI through `npx`, so the `defuddle`
dependency in `package.json` must be installed.

Set API keys in the environment:

```sh
export OPENAI_API_KEY=...
export LINGQ_API_KEY=...
```

Dry runs do not require `LINGQ_API_KEY`. They require `OPENAI_API_KEY` only
when style or difficulty needs automatic assessment; supplying both `--vibe`
and `--lingq-level` skips those OpenAI calls.

## What It Does

```text
article URL
  |
  v
Defuddle article parse
  |
  v
canonical article map
  |
  v
OpenAI text-to-speech + JLPT level estimate
  |
  v
LingQ lesson import
  |
  v
LingQ audio attachment
```

The created lesson includes:

- title
- cleaned article text
- cover image
- OpenAI-generated audio
- description
- source-derived tags
- original URL
- estimated or explicitly selected LingQ level

LingQ tokenizes imported lessons asynchronously, so the app waits for the lesson
lock to clear before attaching audio.

## Difficulty Estimation

`src/lingq_lesson/jlpt_level.clj` sends the cleaned article text to the OpenAI
Responses API using `gpt-5-mini`. The prompt asks for a structured JSON
assessment of the article's JLPT reading difficulty, including vocabulary,
grammar, kanji, sentence complexity, domain difficulty, confidence, and a
justification.

The module maps JLPT levels to LingQ levels before import:

```text
N5 -> 1
N4 -> 2
N3 -> 3
N2 -> 4
N1 -> 5
Above N1 -> 6
```

When `--lingq-level` is omitted, `url_cmd.clj` runs the JLPT estimate in parallel
with text-to-speech generation and uses the returned `lingq-level` as the lesson
`level` sent to LingQ. With `--lingq-level`, the supplied integer is used instead
and the difficulty predictor is skipped. The manual override accepts 1–6;
automatic estimation can also return 6 for material above typical N1 difficulty.

## Tasks

List available tasks:

```sh
bb tasks
```

Run the app:

```sh
bb lingq url https://example.com/article
```

Run tests:

```sh
bb test
```

Format Clojure files:

```sh
bb fmt
```

Check formatting without modifying files:

```sh
clojure -M:fmt check src test scripts
```

## Code Layout

```text
src/lingq_lesson/core.clj    CLI entrypoint and command dispatch
src/lingq_lesson/url_cmd.clj URL command options and orchestration
src/lingq_lesson/parser.clj  Defuddle integration, text cleanup, image download
src/lingq_lesson/openai.clj  Shared OpenAI API helpers
src/lingq_lesson/audio.clj   OpenAI text-to-speech client
src/lingq_lesson/jlpt_level.clj  OpenAI JLPT/LingQ level estimation
src/lingq_lesson/lingq.clj   LingQ import, polling, and audio attachment
scripts/test_runner.clj      Babashka test runner
test/                        Regression tests
.github/workflows/ci.yml     GitHub Actions CI
```

## Current Defaults

- language: Japanese (`ja`) for LingQ API calls
- status: `private`
- level: estimated from article text unless `--lingq-level` (1–6) is supplied
- tags: extracted from article metadata
- voice: auto-classified unless `--voice` or `--vibe` is provided; fallback `alloy`
- vibe: auto-classified unless `--voice` or `--vibe` is provided; fallback `news`
- TTS model: `gpt-4o-mini-tts`
- JLPT model: `gpt-5-mini`

Supported voices:

```text
alloy echo nova onyx shimmer
```

## Notes

Generated lesson/media artifacts such as `.txt`, `.mp3`, `.srt`, and downloaded
images are ignored by Git.

The old standalone pipeline scripts have been removed. The supported workflow is
the app task:

```sh
bb lingq url <article-url>
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
