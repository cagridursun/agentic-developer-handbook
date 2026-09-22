# Lab 02 — Structured Output

**This is structured model output. It is still not an agent.**

## The limitation we found in Lab 01

Lab 01 ended with:

```java
String response = ...;
System.out.println(response);
```

A `String` is perfect when a human reads the answer. It is awkward the moment code has to act on it. If the application needs a title, a summary, a category, and a list of acceptance criteria, someone has to dig those out of prose — and prose has no stable shape to dig into. The model may phrase things differently on every call.

This lab is the answer to that limitation: the model's output arrives as data the application can rely on.

## What problem are we solving?

Application code needs predictable fields. We want to hand Gemini an informal, human-written feature request and get back a value our Java code can use directly — without parsing prose, without guessing at formats.

## What are we building?

```
User request
     ↓
Java application
     ↓
Gemini + response schema
     ↓
Structured response (JSON)
     ↓
DevelopmentTask (Java record)
```

The example input:

> "Users should be able to reset their password from the login page. Send them a temporary link by email and make it expire after 15 minutes."

The example output: a `DevelopmentTask` record with a title, a summary, a `TaskType`, and acceptance criteria.

## Run it

You need Java 21 and a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

Linux/macOS:

```sh
export GOOGLE_API_KEY=your-key
./mvnw -pl labs/02-structured-output compile exec:java
```

Windows (PowerShell):

```powershell
$env:GOOGLE_API_KEY = "your-key"
.\mvnw.cmd -pl labs/02-structured-output compile exec:java
```

Pass your own feature request as arguments:

```sh
./mvnw -pl labs/02-structured-output compile exec:java -Dexec.args="The search page times out when the catalog has more than 10000 items"
```

`GEMINI_MODEL` overrides the default model (`gemini-3.8-flash`). Variable names are in [.env.example](.env.example). Without a key, the program exits with instructions and never calls the API.

## Free-form text vs JSON prompting vs structured output

**Free-form response (Lab 01).** Easy for humans, unpredictable for code. There is nothing to parse reliably.

**"Return JSON" in the prompt.** Better, but still only an instruction. The model may wrap the JSON in polite prose, fence it in ` ```json `, drop a required field, invent a category you never defined, or produce output that is almost JSON. Your code then needs scraping heuristics, and every heuristic is a future bug.

**Structured output.** The schema is not part of the prompt text. It is sent as a separate generation constraint (`responseSchema` plus `responseMimeType("application/json")`), and the API decodes the model's output against it. The response is the JSON — no prose around it, no fences, fields restricted to what the schema defines.

This lab does not scrape JSON out of Markdown, and that is deliberate: if you find yourself stripping ` ```json ` fences, you are prompting for JSON, not using structured output.

## The Java contract

```java
public record DevelopmentTask(
        String title,
        String summary,
        TaskType type,
        List<String> acceptanceCriteria) { ... }

public enum TaskType { FEATURE, BUG, TECHNICAL_TASK }
```

The record is the boundary. Everything after `parse(...)` works with typed fields — `task.type()` is a `TaskType`, not a string that is probably one of three values. The compiler now enforces what downstream code can assume, and a schema/contract mismatch fails loudly at one place instead of corrupting logic quietly somewhere else.

The schema and the record are kept in sync by hand in this lab. That duplication is visible on one screen, and making it visible is the point.

## What just happened?

- **Schema.** A machine-readable description of the allowed output: an object with `title`, `summary`, an enum-restricted `type`, and an array of `acceptanceCriteria`, all required.
- **Generation constraint.** The API used the schema while decoding output, instead of hoping the model follows instructions buried in prose.
- **Deserialization.** Jackson turned the JSON into the `DevelopmentTask` record. An unknown enum value or malformed payload throws immediately.
- **Java object.** From that line on, the model is out of the picture. The rest of the application handles a normal value.
- **Application validation.** `task.problems()` checks what no schema can: a blank title and an empty criteria list are structurally valid and still rejected.

## What we STILL do not have

**This is structured model output. It is still not an agent.**

- No agent — the program makes one call and exits.
- No tools — the model cannot cause any action.
- No memory — a second run knows nothing about the first.
- No RAG — nothing was retrieved.
- No skills — no procedure tells the model how to work.
- No MCP — no external capability is connected.
- No execution loop — there is no second step.

The shape of the response changed. The architecture did not.

## When should I use structured output?

- Classification into known categories
- Extraction of defined fields from unstructured text
- Turning natural language into application commands or data
- Any output that downstream code processes further
- API and database workflows where specific fields must exist

## When should I NOT use it?

- Prose meant directly for a human — an answer, an explanation, an email draft
- Creative writing, where a schema only constrains quality
- Simple summarization where no program parses the result

If nothing in your code reads individual fields, a `String` was already the right type. Lab 01 is not obsolete; it is the simpler tool for a different job.

## Structured does not mean correct

The schema guarantees *syntax*: valid JSON, known fields, `type` from the allowed set. It cannot guarantee *sense*. The model can return a perfectly schema-valid task whose summary misunderstands the request, whose criteria miss the 15-minute expiry, or whose title is an empty-ish string.

That is why `DevelopmentTask.problems()` exists, and why it lives in application code: business rules belong to the application, never to the model. Structured output narrows the failure space; it does not eliminate it. This is still probabilistic inference with a strong constraint on shape.

## Production considerations

Not implemented here, deliberately:

- **Schema evolution** — fields get added; producers and consumers must move in step.
- **Backward compatibility** — old stored outputs must still deserialize, or be migrated.
- **Provider and model support** — structured output support and behavior differ by provider and model; check before switching either.
- **Validation** — real systems validate harder than this lab and decide per rule whether to reject or repair.
- **Failure handling** — a response that fails to parse needs a policy: retry, fall back, or surface the error.
- **Monitoring** — track schema-mismatch and validation-failure rates; a rising rate is an early sign of model or prompt drift.

## What we still cannot do

The model can now hand our application data it understands. It still cannot *do* anything: it cannot create the issue in the tracker, query a live system, call an application function, send the reset email, or look up current data. The application would have to take the `DevelopmentTask` and act on it entirely by itself.

Giving the model a way to request actions — with the application staying in control of executing them — is the next concept: **Milestone 3 — Tools**.

## Sources consulted

Verified on 2026-09-23 against official documentation:

- [Gemini API structured output guide](https://ai.google.dev/gemini-api/docs/structured-output) — supported schema fields (`type`, `properties`, `required`, `items`, `enum`, `description`), enum handling for classification, and the best-practice note that schema-valid output still needs application validation.
- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — `GenerateContentConfig` with `responseMimeType("application/json")` and `responseSchema(...)` on `client.models.generateContent`.
- [java-genai `Schema` Javadoc](https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/Schema.html) — the typed `Schema.builder()` API, including the documented enum pattern (`type: STRING, format: "enum", enum: [...]`).
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — 1.72.0, same version as Lab 01; Jackson (`jackson-databind`) is already the SDK's own JSON dependency.
