# LingQ Lesson

Create a LingQ lesson from an article URL.

The app fetches article content with Defuddle, extracts readable lesson text,
downloads the article image, estimates the article's JLPT difficulty with
OpenAI, maps that estimate to a LingQ lesson level, generates OpenAI
text-to-speech audio, and posts a private Japanese lesson to LingQ.

## Usage

```sh
bb lingq --url https://example.com/article
```

Optional flags:

```sh
bb lingq \
  --url https://example.com/article \
  --voice alloy \
  --vibe news \
  --silent
```

Use `--silent` to suppress the detailed style-classification and JLPT analysis
output. Pipeline progress messages remain visible so you can still follow the
lesson creation process.

Supported voices: `alloy` (male), `echo` (male), `onyx` (male), `nova` (female), `shimmer` (female).

Supported vibes: `business`, `news`, `sports`, `lifestyle`, `technology`.

OpenAI intentionally describes the TTS voices in terms of their character and sound, 
not as explicitly male or female. That said, most listeners perceive them roughly as follows:

OpenAI intentionally describes the TTS voices in terms of their character and sound, not as explicitly male or female. That said, most listeners perceive them roughly as follows:

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
bb lingq --url https://example.com/article

# Force only the vibe; voice falls back to alloy.
bb lingq --url https://example.com/article --vibe technology

# Force only the voice; vibe is inferred from the article style.
bb lingq --url https://example.com/article --voice nova

# Force both values.
bb lingq --url https://example.com/article --vibe lifestyle --voice nova

# Hide detailed analysis while retaining pipeline progress messages.
bb lingq --url https://example.com/article --silent
```

Print help:

```sh
bb lingq --help
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
- estimated LingQ level

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

`core.clj` runs the JLPT estimate in parallel with text-to-speech generation and
uses the returned `lingq-level` as the lesson `level` sent to LingQ.

## Tasks

List available tasks:

```sh
bb tasks
```

Run the app:

```sh
bb lingq --url https://example.com/article
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
src/lingq_lesson/core.clj    CLI entrypoint and orchestration
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
- level: estimated from article text
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
bb lingq --url <article-url>
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
