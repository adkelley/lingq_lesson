# LingQ Lesson Pipeline

A small collection of command-line tools for turning online articles into LingQ
lessons.

The goal is to start with an article URL and end with a new lesson posted to
[LingQ](https://www.lingq.com/) with cleaned text, generated audio, subtitle
timing data, and an optional lesson image.

The scripts are designed as standalone tools that can also be called by an LLM
orchestrator, such as Codex. The scripts handle deterministic transformations
and API calls; the orchestrator can make judgment-based decisions about metadata,
image selection, retries, chunking, and final lesson creation.

## Planned Flow

```text
article URL
  ↓
defuddle CLI
  ↓
article.md
  ↓
extract_text ────────────────┐
  ↓                           │
article.txt                   │
                              │
article.md                    │
  ↓                           │
extract_images                │
  ↓                           │
article-images.txt            │
  ↓                           │
LLM selects image             │
  ↓                           │
prepare_image                 │
  ↓                           │
lesson-cover.jpg              │
                              │
article.txt                   │
  ↓
text_to_speech
  ↓
article.mp3
  ↓
transcribe_audio
  ↓
article.srt
  ↓
post_lingq_lesson ← lesson-cover.jpg
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

### 3. Markdown → Image Links

Use a Babashka script to extract image links from the Markdown article. The
first or preferred image can be passed to the LingQ lesson upload script as the
lesson image.

Planned script:

```text
extract_images.bb
```

Expected contract:

```sh
bb scripts/extract_images.bb article.md > article-images.txt
```

Responsibilities:

- read Markdown from a file argument or stdin
- find Markdown image links like `![alt text](image-url)`
- emit structured JSON to `stdout` with image URL, alt text, and source order
- send logs and errors to `stderr`

Example output:

```json
[
  {
    "index": 0,
    "alt": "Article image alt text",
    "url": "https://example.com/image.jpg"
  }
]
```

For now, the pipeline uses the first extracted image as the default lesson cover.
Later, the LLM orchestrator can use this structured output to choose the best
image candidate.

### 4. Prepare Lesson Image

Use a Babashka script to create a local cover image file from the extracted image
candidate JSON.

Current script:

```text
cover_image.bb
```

Current contract:

```sh
bb cover article-images.json --cover lesson-cover.jpg
```

Current responsibilities:

- read the JSON output from `extract_images.bb`
- select the first image candidate
- download the image URL to a local cover image file

Planned responsibilities:

- support local image files as inputs
- use ImageMagick to create a practical LingQ cover image
- target a 16:9 aspect ratio, such as `1600x900`
- prefer JPEG output when possible
- validate that the output file exists and is non-empty
- fail clearly if ImageMagick is not installed
- emit the final local image path to `stdout`
- send logs and errors to `stderr`

Planned ImageMagick command shape:

```sh
magick input.jpg \
  -resize '1600x900^' \
  -gravity center \
  -extent 1600x900 \
  -quality 85 \
  lesson-cover.jpg
```

Install ImageMagick on macOS:

```sh
brew install imagemagick
```

LingQ does not appear to publish an official lesson cover image file-size limit.
For now, the tool should use practical web-cover defaults:

- 16:9 aspect ratio
- `1600x900` target dimensions
- JPEG output when possible
- target size under roughly 2 MB when practical
- hard maximum around 10 MB unless testing shows otherwise

### 5. Text → Audio

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

### 6. Audio → SRT

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

### 7. Upload LingQ Lesson

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
  --srt article.srt \
  --image lesson-cover.jpg
```

Responsibilities:

- read lesson text, audio, subtitle, and optional image inputs
- authenticate with LingQ
- create or update a LingQ lesson
- report the created lesson URL

Required environment:

```sh
LINGQ_API_KEY=...
```

## Orchestration Model

The preferred orchestration model is an LLM harness, such as Codex, calling these
scripts as tools.

Each script should expose a stable CLI contract and do one deterministic job. The
LLM orchestrator can then coordinate the full workflow and make decisions that
are awkward to hardcode, such as:

- choosing the best article image from extracted image candidates
- deriving or cleaning the lesson title
- selecting the LingQ language code
- deciding whether article text needs chunking before TTS
- retrying failed API calls when appropriate
- checking whether generated artifacts look plausible
- passing the final text, audio, subtitles, image, and metadata to LingQ

This keeps the scripts simple and testable while leaving subjective workflow
choices to the orchestrator.

## Tool Design Guidelines

Prefer small, composable tools that follow Unix-style conventions:

- each script should do one stage well
- each script should provide `--help`
- primary output should go to `stdout` when practical
- logs and errors should go to `stderr`
- failures should exit with a non-zero status
- secrets should come from environment variables
- text transformations should support stdin/stdout where possible
- file-producing stages, such as `.mp3` and `.srt`, should use explicit
  `--output` paths
- structured outputs, such as image candidates or metadata, may support a
  `--json` mode when useful for LLM/tool orchestration

This keeps individual stages testable while still allowing them to be chained
together manually or invoked as tools by an LLM orchestrator.

## Working Directory

The full workflow creates several intermediate artifacts:

- `article.md`
- `article.txt`
- `article-images.json`
- `lesson-cover.jpg`
- `article.mp3`
- `article.srt`

To avoid polluting the current directory, the LLM orchestrator or any future
workflow runner should write these files to a working directory.

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

## Example Manual Workflow

```sh
WORK_DIR=/tmp/lingq_lesson
mkdir -p "$WORK_DIR"

npx defuddle parse "https://example.com/article" \
  --markdown \
  --output "$WORK_DIR/article.md"

bb scripts/extract_text.bb "$WORK_DIR/article.md" > "$WORK_DIR/article.txt"
bb scripts/extract_images.bb "$WORK_DIR/article.md" > "$WORK_DIR/article-images.json"
bb cover "$WORK_DIR/article-images.json" --cover "$WORK_DIR/lesson-cover.jpg"

bb scripts/text_to_speech.bb "$WORK_DIR/article.txt" --output "$WORK_DIR/article.mp3"
bb scripts/transcribe_audio.bb "$WORK_DIR/article.mp3" --output "$WORK_DIR/article.srt"
bb scripts/post_lingq_lesson.bb \
  --title "Example Article" \
  --language ja \
  --text "$WORK_DIR/article.txt" \
  --audio "$WORK_DIR/article.mp3" \
  --srt "$WORK_DIR/article.srt" \
  --image "$WORK_DIR/lesson-cover.jpg"
```

## LLM-Orchestrated Workflow

In an LLM-driven workflow, the user can provide a URL and high-level intent, and
the orchestrator can call the scripts as tools:

```text
User URL
  ↓
LLM orchestrator
  ├─ run Defuddle to produce article.md
  ├─ run extract_text.bb to produce article.txt
  ├─ run extract_images.bb to list image candidates
  ├─ choose title and language
  ├─ run cover_image.bb to produce lesson-cover.jpg from the first image candidate
  ├─ run text_to_speech.bb to produce article.mp3
  ├─ run transcribe_audio.bb to produce article.srt
  └─ run post_lingq_lesson.bb with selected metadata and artifacts
```

The orchestrator should prefer explicit file paths in the working directory and
should inspect command exit statuses before continuing to the next stage.

## Open Questions

- How should article metadata, such as title/source/date, move through the
  pipeline?
- Should each stage emit sidecar JSON metadata in addition to text/audio files?
- What JSON output formats would make the tools easiest for an LLM orchestrator
  to consume?
- Which LingQ fields are required when creating a lesson through the API?
- Should the image extraction stage choose a preferred image automatically, or
  should it emit all image links and let the orchestrator choose?
- How should long articles be chunked for TTS limits, if necessary?
- What checks should the orchestrator perform before posting the final lesson?
- Does LingQ enforce an undocumented maximum image upload size or accepted MIME
  type list?
