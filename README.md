# Mulesoft Reviewer

Reviews a Mulesoft project (XML flow files + Swagger/OpenAPI specs) against a set
of English-language rules, using a local LLM. No XML parsing is performed — raw
files are concatenated and given to the model in full, so it can resolve
cross-file flow references itself. No native binary/executable is shipped or
launched as a subprocess; the model runs in-process via a JNI-bound native
library loaded automatically by the `de.kherud:java-llama.cpp` dependency.

Requires only a JRE 11+ on the machine running the jar (build requires a JDK 11+
and Maven).

## 1. Get a model

Download a quantized GGUF model and place it under `models/`. Recommended
starting point given CPU-only / ~8GB RAM:

- Qwen2.5-7B-Instruct, Q4_K_M quantization, or
- Llama-3.1-8B-Instruct, Q4_K_M quantization

These are typically distributed in GGUF form on Hugging Face (e.g. under
quantization-focused reuploads such as those from `bartowski` or `TheBloke`).
A Q4_K_M 7-8B model is roughly 4-5GB on disk.

```
mulesoft-reviewer/models/qwen2.5-7b-instruct-q4_k_m.gguf
```

## 2. Build

```
mvn clean package
```

This produces `target/mulesoft-reviewer.jar` with all Java dependencies bundled
(the native llama.cpp library is pulled in transitively by the
`java-llama.cpp` dependency and bundled inside the jar for supported platforms).

> Before building for real use, double-check the `de.kherud:java-llama.cpp`
> version/coordinates in `pom.xml` against Maven Central, and confirm the
> method calls in `ModelService.java` (ModelParameters / InferenceParameters /
> LlamaModel) still match that version's API — this library's surface has
> changed across releases.

## 3. Run

Two modes are supported: a local project directory, or cloning directly from
a Git URL (GitHub, Bitbucket, GitLab, or any standard HTTPS git server).
Cloning uses JGit, a pure-Java git client — no native `git` binary required.

**Local directory:**

```
java -jar target/mulesoft-reviewer.jar --local \
    ./project-sample \
    ./rules/rules.md \
    ./models/qwen2.5-7b-instruct-q4_k_m.gguf \
    ./out
```

**Git URL + branch:**

```
java -jar target/mulesoft-reviewer.jar --git \
    https://github.com/your-org/your-mule-project.git \
    main \
    ./rules/rules.md \
    ./models/qwen2.5-7b-instruct-q4_k_m.gguf \
    ./out
```

This performs a shallow clone (depth 1, just the latest commit on the given
branch) into a temporary directory, runs the review, then deletes the clone.

For **private repositories**, set environment variables before running:

```
export GIT_USERNAME=your-username
export GIT_TOKEN=your-personal-access-token
java -jar target/mulesoft-reviewer.jar --git https://bitbucket.org/org/repo.git develop ...
```

Token requirements vary slightly by provider (GitHub accepts a personal
access token as the password with any non-empty username; Bitbucket/GitLab
have their own current conventions for app passwords / access tokens) — check
the provider's docs if authentication fails.

Arguments (local mode): project directory, rules file, model file, optional output dir.
Arguments (git mode): repo URL, branch, rules file, model file, optional output dir.

In both modes the review is printed to the console and written as a
timestamped markdown file under the output directory.

## Project layout

```
mulesoft-reviewer/
├── pom.xml
├── rules/rules.md              # sample English rules — edit/replace freely
├── project-sample/             # sample 2-file Mule project + swagger spec,
│                                  with deliberate issues for testing rules
├── models/                     # put your .gguf model file here (not included)
└── src/main/java/com/yourorg/reviewer/
    ├── Main.java
    ├── io/ProjectLoader.java       # raw file reading + concatenation, no parsing
    ├── io/GitFetcher.java          # pure-Java git clone via JGit (no native git binary)
    ├── rules/RulesLoader.java      # reads rules.md verbatim
    ├── model/ModelService.java     # loads model, runs single review prompt
    └── report/ReportWriter.java    # writes/prints the model's English output
```

## Known limitations / things to validate

- This design assumes "small" projects (a handful of files) that fit inside
  the configured context window (`DEFAULT_CONTEXT_SIZE` in `Main.java`,
  currently 8192 tokens). If you outgrow that, you'll need to either raise the
  context size (costs RAM) or move to a multi-pass, per-file-summary approach.
- Output quality depends heavily on the chosen model; a 7-8B Q4 model on CPU
  is a reasonable balance for judgment-based review, but expect some variance
  and occasional missed or over-flagged issues — treat output as a draft for
  a human reviewer rather than a final verdict.
- The `java-llama.cpp` API calls in `ModelService.java` are written against
  the library's documented shape but have not been run against an actual
  downloaded model in this environment (no model file or internet access to
  Hugging Face was available here) — validate end-to-end on your machine
  before relying on it.
