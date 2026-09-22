# Lab 01 — Your First Model Call

**This is an LLM call. It is not an agent.**

## What problem are we solving?

We want a Java application to send input to a large language model and receive a response. That is the entire problem. Everything else in this handbook builds on this one exchange, so it has to be completely understood before anything is added to it.

## What are we building?

One class, one request, one response.

```
User
 ↓
Java Application
 ↓
Gemini API
 ↓
Model
 ↓
Response
```

The application reads an API key from the environment, sends one prompt to a hosted Gemini model over HTTPS, and prints the text that comes back. There is no loop and no state. When `main` returns, nothing remembers the call happened.

## Run it

You need Java 21 and a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

```sh
export GOOGLE_API_KEY=your-key        # PowerShell: $env:GOOGLE_API_KEY = "your-key"
./mvnw -pl labs/01-model-call compile exec:java
```

Pass your own prompt as arguments:

```sh
./mvnw -pl labs/01-model-call compile exec:java -Dexec.args="Say hello in Turkish"
```

Override the model if you want a different one:

```sh
export GEMINI_MODEL=gemini-3.5-flash-lite
```

The defaults are in [.env.example](.env.example). The key is read from the environment and never printed. If no key is set, the program exits with instructions instead of calling the API.

## What just happened?

- **Application.** Your Java process. It decided what to send, when to send it, and what to do with the answer. Nothing intelligent happened on this side.
- **Provider.** Google's Gemini API. It authenticated your key, routed the request to a model, and returned the result. The provider is a service; the model is what it hosts.
- **Model.** A large language model. It received your text and produced a continuation. It did not run code, browse, or remember you.
- **Inference.** The single act of running the model on your input. You pay per call, mostly proportional to input and output size.
- **Prompt.** The full text the application sent. The model saw exactly that, nothing more.
- **Response.** Text. The application chose to print it; it could have parsed, stored, or discarded it.

## What we do NOT have yet

**This is an LLM call. It is not an agent.**

- No agent: nothing observes a result and decides on a next step.
- No agent loop: the program makes exactly one request and exits.
- No tool calling: the model cannot cause any action in your system.
- No memory: a second run knows nothing about the first.
- No RAG: nothing was retrieved; the model used only its trained weights and your prompt.
- No skills: no instructions telling the model how to perform a task.
- No MCP: there is no external capability to connect to.
- No orchestration: there is no second step to sequence.

If a diagram of this lab and a diagram of an "AI agent" look the same to you, that is the point of the lab: most of what makes an agent an agent is missing here, and the application still does something useful.

## When is this enough?

A direct model call is the right architecture whenever the task is *transform this text*:

- Summarizing a document
- Rewriting a message in another tone or language
- Classifying text into known categories
- Extracting fields from unstructured text, when nothing external has to be looked up or done

If one call solves the problem, stop here. Adding an agent to a summarizer makes it slower, costlier, and harder to debug, and does not make the summary better.

## When is this NOT enough?

- The output must be machine-readable and validated → structured output (Lab 02).
- The model needs to act — query a database, call an API → tools (Lab 03).
- The answer depends on information the model was never trained on → knowledge / RAG (Lab 04).
- The application must remember earlier turns → memory (Lab 05).
- The task needs a procedure, not just a capability → skills (Lab 06).
- The problem takes several steps, and the next step depends on the last result → agent runtime (Lab 07).

## Production considerations

Not implemented here, deliberately. A production caller of this exact code would also need:

- **Timeouts** on the HTTP call, so a slow provider cannot stall your application.
- **Retries** with backoff for transient failures and 429 rate-limit responses.
- **Cost tracking**: every call is billed; log token usage per request.
- **Rate limits**: providers throttle by requests and tokens per minute.
- **Model configuration**: temperature, maximum output tokens, and safety settings are explicit request options, not defaults to inherit blindly.
- **Observability**: record which model, which prompt version, latency, and outcome. The observability milestone covers this properly.

## Sources consulted

Verified on 2026-09-22 against official documentation:

- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — official Google Gen AI Java SDK: client construction, `generateContent` usage, environment variables (`GOOGLE_API_KEY` current, `GEMINI_API_KEY` legacy, `GOOGLE_API_KEY` wins when both are set), and the advice to pin below 2.0.0.
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — version 1.72.0, the latest release at the time of writing.
- [Gemini API model catalog](https://ai.google.dev/gemini-api/docs/models) — `gemini-3.8-flash` is the recommended stable model for new projects; 2.5-family access is limited for new users.
