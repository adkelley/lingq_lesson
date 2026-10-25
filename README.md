# LingQ Lesson

Create a private Japanese LingQ lesson from an article URL.

The app fetches article content with Defuddle, extracts readable lesson text,
downloads the article image, classifies its subject category and estimates JLPT
reading difficulty with TypeSafe or OpenAI, maps that estimate to a LingQ lesson
level, generates OpenAI text-to-speech audio, and posts a private Japanese lesson
to LingQ with timestamps that synchronize the text and audio.

## Language support

Lesson creation and course listing currently support Japanese only. Lessons are
imported into LingQ using `ja`, and difficulty is estimated with the Japanese
JLPT rubric. French and other languages are not supported; a manual level
override does not change the lesson language.

Before downloading the cover or calling a classifier, the app checks Defuddle's
language metadata. Explicit non-Japanese language tags are rejected, including
in dry runs. Japanese tags such as `ja` and `ja-JP` are accepted. Missing or blank
metadata is allowed: this is a metadata guard, not automatic language detection,
so use Japanese articles even when the source does not declare its language.

## Usage

List your Japanese LingQ courses (`ja` is the default and only supported language):

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

Move an existing Japanese lesson to another course:

```sh
bb lingq patch 47139983 --course-id 2948600
```

The positional argument is the lesson ID; `--course-id` is the destination
course ID. Both are required positive integers. The command requires
`LINGQ_API_KEY` and prints the lesson ID and destination course title after
the move succeeds. Currently, `patch` supports changing the course only.

The `url` command takes the article URL as its required positional argument:

```sh
bb lingq url https://example.com/article --classifier openai --category science-technology
bb lingq url https://example.com/article --dry-run --classifier typesafe
```

`--classifier` selects `typesafe` or `openai` (the default). Both assess article
category and Japanese reading difficulty. `--category` overrides the selected
category; `--lingq-level` overrides the resulting lesson level (1–6). These
options override results; the combined classification request still runs.

Dry runs print title, category, and difficulty without downloading the cover,
generating audio, or uploading to LingQ.

Categories follow [Yomiuri's Shinbun's](https://www.yomiuri.co.jp) broad subject sections:

Category | Subject | Default voice
--- | --- | ---
`society` | Crime, courts, accidents, social issues | `alloy`
`politics` | Government, elections, policy | `onyx`
`business` | Economy, companies, markets | `onyx`
`sports` | Athletic competition | `echo`
`international` | Foreign affairs and overseas developments | `alloy`
`regional` | Local communities and regional affairs | `alloy`
`science-technology` | Science, space, IT, engineering | `alloy`
`culture` | Entertainment, arts, books, heritage | `nova`
`lifestyle` | Food, travel, relationships, everyday living | `nova`
`education-careers` | Education, admissions, job hunting | `alloy`
`health` | Medicine, healthcare, wellbeing | `alloy`
`weather` | Weather and forecasts | `alloy`
`other` | Content outside these categories | `alloy`

Classification prefers a specific subject over geography when the subject is
central. Categories determine narration instructions and default voices.
Use `--voice` to override the voice (`alloy`, `echo`, `nova`, `onyx`, or `shimmer`).
The former article `--style` / `--vibe` options are replaced by `--category`.

Other options include `--image <url>` for the cover and `--course-id <id>`
for the destination course. The article URL is required; both it and any
`--image` URL are validated as HTTP or HTTPS URLs at the CLI boundary.

Print general or command-specific help:

```sh
bb lingq --help
bb lingq url --help
bb lingq patch --help
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
export TYPESAFE_API_KEY=...
```

Dry runs do not require `LINGQ_API_KEY`. TypeSafe classification requires
`TYPESAFE_API_KEY`; OpenAI classification and audio generation require
`OPENAI_API_KEY`.

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
TypeSafe or OpenAI category + JLPT assessment
  |
  v
Apply category and difficulty overrides
  |
  v
OpenAI text-to-speech (skipped for dry runs)
  |
  v
LingQ lesson import
  |
  v
LingQ audio attachment
  |
  v
LingQ timestamp generation
```

The created lesson includes:

- title
- cleaned article text
- cover image
- OpenAI-generated audio
- timestamps that synchronize lesson text with the audio
- description
- source-derived tags
- original URL
- estimated or explicitly selected LingQ level

LingQ tokenizes imported lessons asynchronously, so the app waits for the lesson
lock to clear before attaching audio. Once audio processing finishes, the app
asks LingQ to generate timestamps and waits for timestamp generation to finish.

## Difficulty Estimation

The selected classifier evaluates the cleaned article text with two choice
questions: subject category and Japanese reading difficulty. TypeSafe uses
`jev-latest` through its System One API; OpenAI uses `gpt-6-luna` through its
Decisions API. Both use equivalent instructions and descriptions for each
category and JLPT level.

The difficulty rubric considers vocabulary, grammar, kanji, sentence complexity,
and required domain knowledge. The application selects the returned choice and
maps it to a LingQ level; it does not generate a written justification.

The module maps JLPT levels to LingQ levels before import:

```text
N5 -> 1
N4 -> 2
N3 -> 3
N2 -> 4
N1 -> 5
Above N1 -> 6
```

Classification completes before audio generation. When `--lingq-level` is
omitted, the classified level is used for the lesson. When supplied, the integer
(1–6) overrides that result; the combined classification request still runs.
Likewise, `--category` overrides the category result without skipping the request.

Dry runs display the effective category and LingQ level. They also display the
estimated JLPT level when difficulty has not been manually overridden. Model
estimates are approximate and can differ between providers.

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

Tests mock external services, including CLI classifier selection, its TypeSafe
default, invalid classifier rejection, category/voice overrides, and difficulty
overrides. Routine tests do not make paid API calls or real LingQ uploads.

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
src/lingq_lesson/core.clj                       CLI entrypoint and dispatch
src/lingq_lesson/url_cmd.clj                    URL options and orchestration
src/lingq_lesson/courses_cmd.clj                Course listing
src/lingq_lesson/patch_cmd.clj                  Move an existing lesson to another course
src/lingq_lesson/lessons_cmd.clj                Course lesson listing (work in progress)
src/lingq_lesson/parser.clj                     Defuddle, text cleanup, images
src/lingq_lesson/api.clj                        JSON, HTTP errors, header redaction
src/lingq_lesson/openai.clj                     Shared OpenAI configuration
src/lingq_lesson/openai/audio.clj               OpenAI text-to-speech client
src/lingq_lesson/openai/article_classifier.clj  Decisions client and classification
src/lingq_lesson/openai/category_classifier.clj OpenAI category question
src/lingq_lesson/openai/difficulty_classifier.clj OpenAI JLPT rubric and mapping
src/lingq_lesson/typesafe.clj                   TypeSafe System One client
src/lingq_lesson/typesafe/article_classifier.clj TypeSafe classification
src/lingq_lesson/typesafe/category_classifier.clj TypeSafe category question
src/lingq_lesson/typesafe/difficulty_classifier.clj TypeSafe JLPT rubric and mapping
src/lingq_lesson/audio_instructions.clj         Category narration instructions
src/lingq_lesson/lingq.clj                      Import, polling, audio attachment, timestamps
scripts/test_runner.clj                        Babashka test runner
test/                                         Regression tests
.github/workflows/ci.yml                       GitHub Actions CI
```

## Current Defaults

- language: Japanese (`ja`) for LingQ API calls
- status: `private`
- level: estimated from article text unless `--lingq-level` (1–6) is supplied
- tags: extracted from article metadata
- voice: selected from the effective category unless `--voice` is provided; fallback `alloy`
- category: classified by the selected provider unless overridden by `--category`
- TTS model: `gpt-4o-mini-tts`
- classifier: `typesafe` using `jev-latest`
- alternate classifier: `openai` using `gpt-6-luna`

Supported voices:

```text
alloy echo nova onyx shimmer
```

## Roadmap

See [the roadmap](docs/roadmap.md) for TTS provider research, migration planning,
and classifier evaluation follow-up.

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
