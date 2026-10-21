# AGENTS.md

## Project
LingQ Lesson is a Clojure/Babashka CLI that turns article URLs into private
Japanese lessons in LingQ. It uses Defuddle to extract readable text and article
metadata, downloads a cover image, classifies article style and estimates JLPT
difficulty with OpenAI, generates text-to-speech audio, and imports the lesson
into LingQ. Users can choose a course and override style, voice, and difficulty.
See README.md for usage and the JLPT-to-LingQ level mapping.

## Run
- Install Babashka, the Clojure CLI, and Node.js; run `npm install` for Defuddle.
- Create and upload a lesson: `bb lingq url <article-url>`
- List courses: `bb lingq courses ja`
- Inspect options: `bb lingq url --help`
- Start a REPL with `clojure` or `clojure -M:rebel`.

## Code Layout
- `src/lingq_lesson/core.clj`: CLI entrypoint and command dispatch.
- `url_cmd.clj`: URL options and lesson orchestration; `courses_cmd.clj`: course listing.
- `parser.clj`: Defuddle integration, text cleanup, metadata, and image downloads.
- `style_classifier.clj` and `jlpt_level.clj`: OpenAI article assessments.
- `audio.clj` and `audio_instructions.clj`: speech generation and style instructions.
- `openai.clj`: shared OpenAI request, JSON, and error handling helpers.
- `lingq.clj`: lesson import, retry handling, tokenization polling, and audio attachment.
- `test/lingq_lesson/`: regression tests; `scripts/test_runner.clj`: Babashka test runner.

## Environment
- Lesson creation requires `OPENAI_API_KEY` and `LINGQ_API_KEY`; course listing requires the LingQ key.
- Never print, log, or commit API keys
- If exception data includes headers, authorization must be redacted

## Dependencies
- HTTP client: `hato`
- JSON: `cheshire`
- CLI parsing: `babashka.cli`
- Article extraction: Node's `defuddle` CLI, invoked through `npx`

## Editing Guidance
- The user is the primary coder. By default, the agent guides, plans, explains, and reviews rather than implementing changes.
- Help the user develop idiomatic Clojure: explain design tradeoffs, encourage small functions and clear data flow, and review correctness, validation, concurrency, and appropriate tests.
- Offer focused examples and incremental next steps so the user can write and understand the code. Make code changes only when the user explicitly requests implementation; direct requests to update documentation authorize those documentation edits.
- Keep this repo simple and educational.
- Prefer small functions with clear names
- Keep CLI orchestration separate from article parsing and API clients.
- Coerce and validate CLI options at the boundary; preserve automatic behavior when overrides are absent.
- Preserve concurrency between audio generation and difficulty assessment when assessment is needed.
- LingQ tokenizes imports asynchronously: wait for the lesson lock to clear before attaching audio.
- Mock external services in tests; avoid paid API calls or real LingQ uploads during routine verification.
- Do not replace working code with large abstractions unless requested.

## Commits
- Follow the commit message approach described in `docs/commit.md`
- Prefer commit messages in the form `<symbol> <scope> - <summary>`

## Verification
- Run regression tests with `bb test`.
- Check formatting with `clojure -M:fmt check src test scripts` (also run by CI).
- Use `bb fmt` to format code when appropriate; avoid reformatting unrelated user edits.
- For CLI changes, check `bb lingq url --help` and add focused parsing/behavior tests with mocked services.
