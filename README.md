# GovernancePlus — Rules-Based Governance for Mule, POM & Swagger

Reviews a Mulesoft project with a **100% deterministic, rules.json-driven rule
engine** — no AI is ever involved in deciding what passes or fails:

- **Rule engine** (`com.governanceplus.reviewer.ruleengine`, extracted from an
  Eclipse plugin into `reviewer-core`) — a deterministic validator covering three
  rule categories, all sourced from one `rules.json`:
  - `rules` — XPath expressions checked against Mule XML flows (`src/main/mule/**/*.xml`)
  - `pomRules` — minimum dependency versions checked against `pom.xml`
  - `swaggerRules` — JSONPath expressions checked against Swagger/OpenAPI specs (`src/main/resources/api/**/*.{yaml,yml,json}`)

  Fast, no LLM involved, no false ambiguity — a rule either matches or it doesn't.
- **Rules management UI** (`/rules`) — view, add, edit, and delete rules in
  every category, and **test a rule against pasted sample input** before
  saving it. This is the intended way to maintain `rules.json` day to day.
- **AI rule-authoring assist** — a local LLM (`ModelService`, in-process via
  the JNI-bound `de.kherud:java-llama.cpp` library, no subprocess/network port)
  helps **write or explain a rule** from the Rules UI. It is never on the
  review path — `RuleEngineReviewAdapter` never calls the model, and the
  model never sees a project's files. Its only reachable surface is
  `/api/rules/assist/**`.

The project has two ways to run a review:

- **CLI** (`reviewer-core`) — one-shot command, rule engine only.
- **Web app** (`reviewer-web` + `frontend`) — a Spring Boot API and React UI
  where you upload a project zip, manage rules, watch the review run, and get
  AI help authoring rules.

## Project layout

```
governanceplus/
├── pom.xml                 # Maven aggregator (modules: reviewer-core, reviewer-web)
├── reviewer-core/          # shared review engine + CLI, reused by reviewer-web
│   └── src/main/java/com/governanceplus/reviewer/
│       ├── ruleengine/                    # extracted rule engine (XML + pom.xml + Swagger/OpenAPI)
│       │   ├── {RuleEngine,RuleLoader,XPathEvaluator,SwaggerRuleEvaluator,
│       │   │    PomDependencyValidator,MuleRuleValidatorMojo}.java
│       │   └── model/{Rule,RuleConfig,PomRule,SwaggerRule,Violation,ComparableVersion}.java
│       ├── Main.java              # CLI entry point
│       ├── io/GitFetcher.java
│       ├── report/{ReportWriter,ReviewReportMarkdownRenderer}.java
│       └── model/{ChatEngine,ModelService,RuleEngineReviewAdapter,
│                  ReviewReport,ReviewFinding}.java
├── reviewer-web/           # Spring Boot 2.7.x (Java 11) REST API
│   └── src/main/java/com/governanceplus/web/
│       ├── GovernancePlusApplication.java
│       ├── config/{ModelConfig,WebConfig}.java
│       ├── controller/{ReviewController,RulesController,RuleTestController,RuleAssistController}.java
│       └── service/{ReviewJobRegistry,ReviewJob,ProjectZipExtractor,
│                     RulesFileStore,RuleTestService,MockChatEngine}.java
├── frontend/               # React (Vite + TypeScript) UI, built into reviewer-web's jar
├── mvnw, mvnw.cmd, .mvn/   # Maven Wrapper — use these instead of a system-installed mvn
├── rules/rules.json.example # ILLUSTRATIVE starter for the rule engine — not a real ruleset
├── project-sample/         # sample Mule project (src/main/mule + pom.xml + src/main/resources/api)
└── models/                 # put your .gguf model file here (not included)
```

## 1. Set up rules

The authoritative ruleset lives in `rules.json`, with three categories:
`rules` (XPath, against Mule XML), `pomRules` (minimum dependency versions),
and `swaggerRules` (JSONPath, against OpenAPI specs). `rules/rules.json.example`
is an **illustrative starter only, not a real ruleset** — copy it to
`rules/rules.json` and replace with your own rules, or point
`governanceplus.rules.path` (web) / the CLI's `<rulesJsonFile>` arg at
wherever your real file lives.

Day to day, manage rules through the **Rules page** in the web UI (`/rules`)
rather than hand-editing the file: it lists every rule per category, lets you
add/edit/delete, and lets you test a rule against pasted sample input before
saving. See "Rule shape" below for what each category's editor writes.

### Rule shape (`rules.json`)

```json
{
  "rules": [
    {
      "id": "SEC-001",
      "category": "Security",
      "severity": "CRITICAL",
      "description": "Every HTTP Listener config must have TLS/HTTPS enabled",
      "xpath": "//http:listener-config[not(@protocol='HTTPS')]"
    }
  ],
  "pomRules": [
    { "artifactId": "mule-http-connector", "minVersion": "1.6.0" }
  ],
  "swaggerRules": [
    {
      "id": "SWAGGER-001",
      "category": "API Design",
      "severity": "MAJOR",
      "description": "Every operation should document at least one 4xx or 5xx error response",
      "jsonPath": "$.paths[*][*][?(!@.responses['400'] && !@.responses['404'] && !@.responses['500'])]",
      "projectNamePattern": "*-api"
    }
  ]
}
```

`xpath` is evaluated per-file under `src/main/mule/`, with every `xmlns:`
prefix declared on that file's root element dynamically registered — so
rules can use whatever prefixes (`http:`, `db:`, etc.) the target files
actually declare. `jsonPath` is evaluated per-file under
`src/main/resources/api/` against the OpenAPI spec parsed into a JSON tree
(YAML or JSON source both work). `severity` is free text, mapped
case-insensitively into `CRITICAL`/`MAJOR`/`MINOR` (contains "CRIT" →
CRITICAL, "ERROR"/"MAJOR" → MAJOR, else → MINOR). The engine only reports
violations — it has no notion of a rule "passing".

Every rule in every category (`rules`/`pomRules`/`swaggerRules`) also accepts
an optional **`projectNamePattern`** — a glob (e.g. `*-api`, `mule-*-service`)
matched against the target project's pom.xml `<artifactId>` (the direct child
of `<project>`, not a nested `<parent>`/`<dependency>` artifactId). A rule
with no pattern (or a blank one) applies to every project, same as before
this field existed. If the project's own artifactId can't be determined (no
`<artifactId>` in its pom.xml, or no pom.xml at all), a rule with a pattern
is left applicable rather than silently skipped — an unknown project name
isn't the same as "explicitly out of scope." This is how you keep one shared
`rules.json` across many projects while still having some rules (e.g.
Swagger/API-design checks) only fire for the projects they're relevant to.

## 2. Get a model (only needed for AI rule-authoring assist)

Download a quantized GGUF model and place it under `models/`. Recommended
starting point given CPU-only / ~8GB RAM:

- Qwen2.5-7B-Instruct, Q4_K_M quantization, or
- Llama-3.1-8B-Instruct, Q4_K_M quantization

These are typically distributed in GGUF form on Hugging Face (e.g. under
quantization-focused reuploads such as those from `bartowski` or `TheBloke`).
A Q4_K_M 7-8B model is roughly 4-5GB on disk.

A model is only needed for the Rules page's "Ask AI" / "Explain" buttons —
reviews never load it, and it never sees a project's files. You don't need a
model to try the web app end-to-end either way — see "Mock mode" below.

## 3. CLI usage

```
mvn -f reviewer-core/pom.xml clean package
```

Produces `reviewer-core/target/governanceplus-cli.jar` — a shaded, standalone
runnable jar with all Java dependencies bundled. (`reviewer-core/target/governanceplus.jar`
is the plain/thin module artifact `reviewer-web` depends on in the reactor
build — not runnable standalone; the shaded jar is a separate attached
artifact specifically so `reviewer-web`'s own fat jar doesn't end up bundling
a second, duplicate copy of every one of reviewer-core's dependencies.)

```
java -jar reviewer-core/target/governanceplus-cli.jar --local \
    ./project-sample ./rules/rules.json.example ./out

java -jar reviewer-core/target/governanceplus-cli.jar --git \
    https://github.com/your-org/your-mule-project.git main \
    ./rules/rules.json.example ./out
```

The CLI is rule-engine only — it never loads a model, so there's no
`.gguf` argument.

For private repos in `--git` mode, set `GIT_USERNAME` / `GIT_TOKEN` env vars
first. See `reviewer-core/src/main/java/com/governanceplus/reviewer/io/GitFetcher.java`
for provider-specific token notes.

## 4. Web app usage

### Option A: single bundled jar (recommended)

Use the Maven Wrapper (`mvnw`/`mvnw.cmd`) checked into the repo root — it
pins the exact Maven version this build was verified against, so you don't
need Maven installed separately. Building `reviewer-web` also builds the
React app (via `frontend-maven-plugin`, using its own isolated Node/npm — it
does not touch or require any globally installed Node) and copies
`frontend/dist` into `reviewer-web`'s classpath under `static/`, so the final
jar serves both the UI and the API on one port:

```
./mvnw.cmd clean package          # Windows
./mvnw clean package              # macOS/Linux

java -jar reviewer-web/target/reviewer-web.jar
```

Then open `http://localhost:8081` — that's the React UI, backed by the same
process's `/api/**` endpoints. A `WebMvcConfigurer` resource-handler fallback
(`reviewer-web`'s `WebConfig`) serves `index.html` for any unmatched
non-`/api` path, so client-side routes like `/reviews/{jobId}` also work on a
direct browser refresh, not just in-app navigation.

The first build downloads a pinned Node version into `frontend/.node-tools/`
(gitignored, persists across `mvn clean` — cached there deliberately so
`clean` doesn't have to delete/re-download a whole Node install every time)
and runs `npm ci && npm run build`. Subsequent builds reuse that cache.

### Option B: backend + frontend as separate dev processes (faster iteration)

Invoking a Maven goal directly (rather than a lifecycle phase) skips the
frontend-build step, so this is the quicker loop when only iterating on one
side:

```
mvn -f pom.xml -pl reviewer-web -am spring-boot:run
```

By default this starts in **mock mode** (`governanceplus.model.mock=true`),
so the AI-assist endpoints return a canned response with no model loaded —
useful for trying the API or developing the frontend without a multi-GB
model file. To use a real model, override in
`reviewer-web/src/main/resources/application.yml` or via command-line flags:

```
java -jar reviewer-web/target/reviewer-web.jar \
    --governanceplus.model.mock=false \
    --governanceplus.model.path=../models/qwen2.5-7b-instruct-q4_k_m.gguf \
    --governanceplus.model.context-size=4096
```

The model loads **once** at startup as a singleton bean, not per-request —
loading a GGUF model is too expensive to repeat per HTTP call.

Key config (`application.yml`):

| Property | Default | Meaning |
|---|---|---|
| `governanceplus.model.mock` | `true` | Use the canned `MockChatEngine` instead of a real model |
| `governanceplus.model.path` | `""` | Path to a `.gguf` file (required when `mock=false`) |
| `governanceplus.model.context-size` | `4096` | Context window size in tokens |
| `governanceplus.model.threads` | `0` | CPU threads for inference; `0` = auto-detect (all available processors) |
| `governanceplus.model.max-output-tokens` | `150` | Cap on generated tokens per assist response — kept modest since generation is CPU-only (measured ~3-4 tokens/sec with a 3B Q4 model on modest hardware); raise it for longer answers at the cost of slower responses |
| `governanceplus.rules.path` | `../rules/rules.json` | The single, authoritative rules.json (XML/pom/Swagger rules) — read by the rule engine and by the Rules management UI |
| `governanceplus.jobs.ttl-minutes` | `120` | How long a completed review job is kept in memory before being purged |

There is no database — jobs and extracted project files are in-memory/on-disk
and purged after the TTL, and `rules.json` itself is the persistent store for
rules. There is no authentication. Assist requests are stateless — each
generate/explain call is sent to the model on its own, with no server-side
conversation history.

A review job runs the **rule engine** against the whole extracted project
directory (see `RuleEngineReviewAdapter`) and returns one report:
`ReviewReport` — a summary plus structured `ReviewFinding`s (rule, category,
status, severity, file, explanation, recommendation). Every finding has
`status=FAIL`, since the rule engine only reports violations and has no PASS
concept. If `governanceplus.rules.path` doesn't point at a real file, the
whole job fails with a clear error message rather than a raw stack trace. The
React UI renders the report in `EngineReportSection`: a dashboard with
pass/fail/warning counts, severity breakdown, and filterable findings grouped
by category. It supports "Print / Export" (the browser's print dialog).

REST API:

- `GET /api/rules` — the full rules.json, structured as `{rules, pomRules, swaggerRules}`
- `POST /api/rules/xml`, `PUT /api/rules/xml/{id}`, `DELETE /api/rules/xml/{id}` — XML rule CRUD
- `POST /api/rules/pom`, `PUT /api/rules/pom/{artifactId}`, `DELETE /api/rules/pom/{artifactId}` — pom rule CRUD
- `POST /api/rules/swagger`, `PUT /api/rules/swagger/{id}`, `DELETE /api/rules/swagger/{id}` — swagger rule CRUD
- `POST /api/rules/{xml,pom,swagger}/test` — test a not-yet-saved rule against pasted sample input, returns `{matched, violations}`
- `POST /api/rules/assist/generate` (`{category, instruction}`) → `{suggestion}` — AI helps draft a rule
- `POST /api/rules/assist/explain` (`{category, rule}`) → `{explanation}` — AI explains an existing rule
- `POST /api/reviews` (multipart: exactly one of `file`=zip or `projectPath`=a path on
  the server's filesystem) → `202 {jobId, status}` — always runs against the current `rules.json`
- `GET /api/reviews/{jobId}` → `{jobId, status, report, errorMessage}`,
  where `report` is `{summary, findings}` or `null`

For the frontend side of this faster loop:

```
cd frontend
npm install
npm run dev
```

Open the printed local URL (typically `http://localhost:5173`). The Vite dev
server proxies `/api/**` to the backend on `http://localhost:8081` (see
`frontend/vite.config.ts`), so no CORS setup is needed in dev. Upload a zip of
`project-sample/` to try the review flow end-to-end, or open the Rules page to
manage rules and try AI assist.

**Zip structure**: `src/main/mule/` and `pom.xml` should be at the zip's top
level. If you zip a project by right-clicking the project *folder* itself
("Compress"/"Send to > zip"), most OS tools wrap the contents in one extra
parent directory — `myproject.zip` containing `myproject/src/main/mule/...`
rather than `src/main/mule/...` directly. `RuleEngineReviewAdapter` detects
and unwraps exactly one level of that automatically, so either form works;
deeper nesting won't be found.

**Server path, as an alternative to uploading a zip**: the "New Review" page
also has a "Server path" option — type in a path instead of picking a file,
and the backend reads that directory directly (never deleted afterward,
unlike an uploaded zip's temp extraction). This is a path *on the machine
running the backend*, not necessarily your own browser's machine — for local
dev they're usually the same box, which is the only case this is really
meant for. There is no access restriction on this beyond whatever the OS
already grants the backend process (consistent with this app's no-auth,
single-trusted-user design): anyone who can reach this endpoint can ask the
backend to read any directory it has permission to read. Don't expose this
past localhost/a trusted network without adding your own access control in
front of it.

Recently-run job IDs are kept in the browser's `localStorage` only (no backend
history) so you can find your last few reviews.

## What got extracted from com.bhanu.autoreview, and what didn't

The original `com.bhanu.autoreview` was an Eclipse IDE plugin. Its logic was
extracted into `reviewer-core` under `com.governanceplus.reviewer.ruleengine`
(repackaged from the original `com.bhanu.autoreview`, so the whole codebase
stays under one namespace): `RuleEngine`, `RuleLoader`, `XPathEvaluator`,
`PomDependencyValidator`, `MuleRuleValidatorMojo`, and the `Rule`/`RuleConfig`/
`PomRule`/`Violation`/`ComparableVersion` model classes. **Dropped**, not
carried over:

- `ReviewHandler`, `ScrollableMessageDialog`, `Activator`, `plugin.xml` —
  Eclipse-UI/OSGi-only, not applicable outside the IDE.
- `NamespaceExtractor`, `DynamicNamespaceContext`, `LineNumberUtil` — dead
  code even in the original plugin; `XPathEvaluator` has its own private
  equivalents and never called these.
- `HtmlReportGenerator` — already commented out at its only call site in the
  original `MuleRuleValidatorMojo`; wrote to a hardcoded `target/` path that
  doesn't fit either the CLI's or the web app's output model.
- The bundled `dom4j` jar — never actually imported by any class that made it
  into `reviewer-core`.

## Known limitations / things to validate

- **`rules/rules.json.example` is illustrative, not a real ruleset.** Copy it
  and replace with your actual rules before relying on the rule engine's
  output for anything real.
- XPath has no native regex/pattern support in the subset `XPathEvaluator`
  uses, so rules like "flow names shouldn't look like `flow1`/`Flow_2`" can
  only be expressed as exact-match alternatives (`@name='flow1'`), not a
  general naming-convention check — a real generic naming rule would need
  either enumerating known-bad names or a different evaluation approach.
- Swagger/JSONPath rules have no reliable source-line info once the spec is
  parsed into a JSON tree (same as pom rules), so swagger findings report the
  file but not a line number.
- The rule engine's output is deterministic: the same project + rules.json
  always produces the same violations. AI assist is the one non-deterministic
  part of the app, and it is scoped to helping draft/explain a rule — it never
  runs during a review and never influences pass/fail.
- The `java-llama.cpp` API calls in `ModelService.java` are written against
  the library's documented shape but have not been run against an actual
  downloaded model in this environment — validate end-to-end on your machine
  before relying on it for AI assist. The web app's job/API/UI plumbing, the
  rule engine (`RuleEngineReviewAdapter`, tested against a real fixture XML
  file, pom.xml, and Swagger spec), and mock-mode assist *have* all been
  verified end-to-end.
- No streaming: assist calls wait for the full response rather than
  streaming tokens as they're generated. `ModelService` already streams
  internally, so wiring up SSE/WebSocket is a natural follow-up if needed.
- No git-URL submission from the web UI (zip upload only) — `GitFetcher`
  remains available for the CLI's `--git` mode.
- `RulesFileStore`'s concurrency safety (a `synchronized` in-process lock) is
  scoped to this app's existing no-auth, single-trusted-user design — it
  doesn't protect against something else editing `rules.json` on disk at the
  same time.
