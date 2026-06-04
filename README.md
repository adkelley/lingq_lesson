# LingQ Lesson Pipeline

A small collection of scripts for turning online articles into LingQ lessons.

The goal is to start with an article URL and end with a new lesson posted to
[LingQ](https://www.lingq.com/) with cleaned text, generated audio, and subtitle
timing data.

## Planned Flow

```text
article URL
  ↓
defuddle CLI
  ↓
article.md
  ↓
extract_text
  ↓
article.txt
  ↓
text_to_speech
  ↓
article.mp3
  ↓
transcribe_audio
  ↓
article.srt
  ↓
post_lingq_lesson
  ↓
LingQ lesson
```

## Pipeline Stages

### 1. URL → Markdown

Use [`defuddle`](https://www.npmjs.com/package/defuddle) to fetch an article URL,
extract the main article content, and write Markdown to a file.

Write Markdown to a file:

```sh
npx defuddle parse <url> --markdown --output <output_file>
```

Or emit Markdown to `stdout` for piping:

```sh
npx defuddle parse <url> --markdown
```

Expected file-output contract:

```sh
npx defuddle parse "https://example.com/article" --markdown --output article.md
```

Expected stdout contract:

```sh
npx defuddle parse "https://example.com/article" --markdown
```

Responsibilities:

- fetch the article URL
- extract the main readable article content
- write Markdown to the requested output file, or emit Markdown to `stdout`
- send logs and errors to `stderr`

### 2. Markdown → Plain Text

Use the Babashka text extraction script to remove Markdown formatting and produce
plain article text.

Current script:

```text
scripts/extract_text.bb
```

Expected contract:

```sh
bb scripts/extract_text.bb article.md > article.txt
```

Eventually this script should also support stdin so it can consume Markdown
directly from Defuddle:

```sh
npx defuddle parse "https://example.com/article" --markdown \
  | bb scripts/extract_text.bb \
  > article.txt
```

Responsibilities:

- validate Markdown input when reading from a file
- remove Markdown structural lines
- strip common inline Markdown syntax
- remove links/images by default
- optionally preserve Markdown link text with `--keep-link-text`
- split cleaned article text into readable sentence lines
- emit plain text to `stdout`

### 3. Text → Audio

Use OpenAI text-to-speech to generate an audio file from the cleaned article text.

Planned script:

```text
text_to_speech.bb
```

Expected contract:

```sh
bb scripts/text_to_speech.bb article.txt --output article.mp3
```

Responsibilities:

- read the cleaned article text
- call the OpenAI TTS API
- write an `.mp3` file
- keep API keys in environment variables, not source files

Required environment:

```sh
OPENAI_API_KEY=...
```

### 4. Audio → SRT

Use OpenAI Whisper/transcription to generate subtitle timing data from the audio.

Planned script:

```text
transcribe_audio.bb
```

Expected contract:

```sh
bb scripts/transcribe_audio.bb article.mp3 --output article.srt
```

Responsibilities:

- read the generated `.mp3` file
- call OpenAI transcription/Whisper
- request or convert output into `.srt`
- write subtitle timing data to disk

Note: Whisper is speech-to-text. It creates the transcript/subtitles from the
audio; it does not generate the audio itself.

### 5. Upload LingQ Lesson

Use the LingQ API to create a new lesson from the generated artifacts.

Planned script:

```text
post_lingq_lesson.bb
```

Expected contract:

```sh
bb scripts/post_lingq_lesson.bb \
  --title "Article title" \
  --language ja \
  --text article.txt \
  --audio article.mp3 \
  --srt article.srt
```

Responsibilities:

- read lesson text, audio, and subtitle files
- authenticate with LingQ
- create or update a LingQ lesson
- report the created lesson URL

Required environment:

```sh
LINGQ_API_KEY=...
```

## Script Design Guidelines

Prefer small, composable scripts that follow Unix-style conventions:

- each script should do one stage well
- primary output should go to `stdout` when practical
- logs and errors should go to `stderr`
- secrets should come from environment variables
- text transformations should support stdin/stdout where possible
- file-producing stages, such as `.mp3` and `.srt`, should use explicit
  `--output` paths

This keeps individual stages testable while still allowing them to be chained
together.

## Working Directory

The full workflow creates several intermediate artifacts:

- `article.md`
- `article.txt`
- `article.mp3`
- `article.srt`

To avoid polluting the current directory, the eventual orchestrator should write
these files to a working directory.

Default:

```text
/tmp/lingq_lesson
```

Override:

```sh
--work-dir ./work/yomiuri
```

Low-level scripts should stay simple and accept explicit input/output paths. The
orchestrator should be responsible for resolving `--work-dir`, creating it if
needed, and passing concrete paths to each stage.

## Example Development Workflow

```sh
WORK_DIR=/tmp/lingq_lesson
mkdir -p "$WORK_DIR"

npx defuddle parse "https://example.com/article" \
  --markdown \
  --output "$WORK_DIR/article.md"

bb scripts/extract_text.bb "$WORK_DIR/article.md" > "$WORK_DIR/article.txt"
bb scripts/text_to_speech.bb "$WORK_DIR/article.txt" --output "$WORK_DIR/article.mp3"
bb scripts/transcribe_audio.bb "$WORK_DIR/article.mp3" --output "$WORK_DIR/article.srt"
bb scripts/post_lingq_lesson.bb \
  --title "Example Article" \
  --language ja \
  --text "$WORK_DIR/article.txt" \
  --audio "$WORK_DIR/article.mp3" \
  --srt "$WORK_DIR/article.srt"
```

## Open Questions

- How should article metadata, such as title/source/date, move through the
  pipeline?
- Should each stage emit sidecar JSON metadata in addition to text/audio files?
- Should the final orchestrator be a shell script, a Babashka task, or remain a
  set of independent commands?
- Which LingQ fields are required when creating a lesson through the API?
- How should long articles be chunked for TTS limits, if necessary?
