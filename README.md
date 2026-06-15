# LingQ Lesson

Create a LingQ lesson from an article URL.

The app fetches article content with Defuddle, extracts readable lesson text,
downloads the article image, generates OpenAI text-to-speech audio, and posts a
private Japanese lesson to LingQ.

## Usage

```sh
bb lingq --url https://example.com/article
```

Optional flags:

```sh
bb lingq \
  --url https://example.com/article \
  --voice cedar \
  --vibe newscaster
```

Print help:

```sh
bb lingq --help
```

## Requirements

Install Babashka and Node dependencies:

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
OpenAI text-to-speech
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
- tags
- original_url

LingQ tokenizes imported lessons asynchronously, so the app waits for the lesson
lock to clear before attaching audio.

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

## Code Layout

```text
src/lingq_lesson/core.clj    CLI entrypoint and orchestration
src/lingq_lesson/parser.clj  Defuddle integration, text cleanup, image download
src/lingq_lesson/audio.clj   OpenAI text-to-speech client
src/lingq_lesson/lingq.clj   LingQ import, polling, and audio attachment
scripts/test_runner.clj      Babashka test runner
test/                        Regression tests
```

## Current Defaults

- language: Japanese (`ja`) for LingQ API calls
- status: `private`
- level: `3`
- tags: `yomiuri`, `news`
- voice: `cedar`
- vibe: `newscaster`
- TTS model: `gpt-4o-mini-tts`

Supported voices:

```text
alloy ash ballad cedar coral echo fable marin nova onyx sage verse
```

## Notes

The old standalone pipeline scripts have been removed. The supported workflow is
the app task:

```sh
bb lingq --url <article-url>
```
