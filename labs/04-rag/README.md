# Lab 04 — Knowledge / RAG

**RAG is retrieve, augment, generate.**

**RAG is not a vector database.**

This is still not an agent.

## The limitation we found

Lab 01 produced text, Lab 02 produced structured data, and Lab 03 let the model request an application capability. All three worked from the model's trained weights plus whatever the prompt contained.

Now the user asks: *"How does authentication work in our platform?"* The answer exists — in private project documentation. It is not in the model's training data, it is not conversational memory, it should not be baked into model weights, and it is not an action to perform. It is knowledge the application owns, and the model cannot see it unless the application puts it into the context.

## What problem are we solving?

A user asks questions about a fictional internal software platform. The answers live in local Markdown documentation. The application must find the relevant documents and hand them to the model along with the question.

## What are we building?

```
Question
   |
   v
Local documentation (5 Markdown files)
   |
   v
Retriever (lexical scoring, plain Java)
   |
   v
Relevant documents (top 2, with scores)
   |
   v
Augmented prompt
   |
   v
Gemini
   |
   v
Grounded answer
```

## The three parts of RAG

1. **Retrieval** — `LocalRetriever.retrieve(...)` scores each document against the question and keeps the best matches. This is application code that runs *before* any model call. The model does not choose what to read.
2. **Augmentation** — `RagExample.buildPrompt(...)` builds one explicit prompt: instructions, the question, and only the selected documents, each wrapped in a `<document source="...">` tag.
3. **Generation** — one ordinary `generateContent` call, exactly like Lab 01. The only thing that changed since Lab 01 is what the prompt contains.

## Our knowledge corpus

Five short Markdown files under `src/main/resources/knowledge/` describe **Helio**, a fictional developer platform invented for this lab — every file says so in its header, and nothing in them describes a real company or product:

- `architecture.md` — modular monolith, the Beacon event bus, one artifact per release
- `authentication.md` — 45-minute access tokens, 30-day rotating refresh tokens, mTLS between workloads
- `coding-guidelines.md` — Java 21, records, deterministic tests, event versioning
- `deployment.md` — dev → staging → production, two release-captain approvals, 10-minute automatic rollback
- `services.md` — five modules and the teams that own them

The facts are distinctive on purpose: when the model answers "tokens live for 45 minutes, per authentication.md", you can see grounding working. Each file is small enough to act as one retrieval unit, so this lab has no chunking system.

## Retrieval without embeddings

The retriever is deliberately simple: lowercase the text, split on non-alphanumerics, drop a small stopword list, crudely singularize plurals, then score each document by counting distinct question terms found in its content (+1 each) and its filename (+2 each). Zero-score documents are discarded, ties break alphabetically, and the top 2 survive.

This is not production-grade information retrieval, and the code says so. For a five-file corpus it is entirely sufficient to teach the architecture — and that is the point: **the RAG pattern is independent of the retrieval technique.** Embeddings become useful when you hit vocabulary mismatch, paraphrases, much larger corpora, or when semantic similarity matters more than shared terms. Even then, they are one retrieval strategy among several. Embedding-based, vector-store-backed, and hybrid retrieval are natural future ecosystem examples; they are intentionally not part of this canonical lab.

## Run it

You need Java 21 and a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

Linux/macOS:

```sh
export GOOGLE_API_KEY=your-key
./mvnw -pl labs/04-rag compile exec:java
```

Windows (PowerShell):

```powershell
$env:GOOGLE_API_KEY = "your-key"
.\mvnw.cmd -pl labs/04-rag compile exec:java
```

Useful questions to try:

```sh
./mvnw -pl labs/04-rag compile exec:java -Dexec.args="How does authentication work?"
./mvnw -pl labs/04-rag compile exec:java -Dexec.args="What happens during a production deployment?"
./mvnw -pl labs/04-rag compile exec:java -Dexec.args="Which service owns notifications?"
```

And one the corpus cannot answer:

```sh
./mvnw -pl labs/04-rag compile exec:java -Dexec.args="Best banana smoothie recipe?"
```

That last one prints "No relevant project documentation was found for this question." and never calls Gemini. `GEMINI_MODEL` overrides the default model (`gemini-3.8-flash`); variable names are in [.env.example](.env.example).

## What was retrieved?

Before calling the model, the application prints its own retrieval decision:

```
Retrieved sources:
- authentication.md (score: 3)
```

This list comes from application retrieval logic, never from a model claim. The prompt also asks the model to mention source filenames in its answer — useful for readers, but do not rely on the model for provenance. The application knows what it retrieved; the model only claims what it used.

## How augmentation works

A shortened view of the actual prompt:

```
You are answering a question about the Helio platform using the supplied
project documentation.

Use only the provided context when answering, and mention which source file
the information comes from. If the context does not contain enough
information, say that the available documentation is insufficient.

Question:
How does authentication work?

Retrieved context:

<document source="authentication.md">
...file content...
</document>
```

The `<document>` tags are structure, not a security boundary. Retrieved documents are untrusted input regardless of how they are wrapped: text inside them could try to impersonate instructions. This lab keeps the corpus fictional and local; the production concern is called prompt injection and is discussed below.

## RAG is not a vector database

RAG is an architectural pattern: *retrieve relevant knowledge, augment the prompt, generate an answer.* Vector similarity search is one possible implementation of the retrieve step. So is a SQL query, a full-text index, an API call, or the term counting in this lab.

If someone says "we need RAG" and means "we need to install a vector database", the concepts have been swapped. Choose the retrieval mechanism after you understand the retrieval problem — and for many problems, the simplest mechanism wins.

## RAG vs normal lookup

If the application already knows exactly what data it needs:

```sql
SELECT status FROM service WHERE id = ?
```

or `GET /users/123` — then a deterministic lookup is simpler, cheaper, easier to test, and more authoritative than any retrieval pipeline. Do not label every database query as RAG. Use RAG when the application must *find relevant pieces of mostly unstructured knowledge* whose location it does not know in advance.

## Knowledge vs Tools

Tool calling (Lab 03) asks: *what capability should the application execute?* The model requests, the application acts.

Knowledge / RAG (this lab) asks: *what information should be placed into the model's context before it generates?* The application retrieves, the model reads.

In Lab 03 the flow was model → application capability. Here it is application → knowledge → model, and retrieval runs before inference under full application control. Retrieval is deliberately not exposed as a Gemini tool in this lab; that composition — a model deciding when to search — is a later topic.

## Knowledge vs Memory

The Helio documentation exists whether or not this conversation ever happened. That makes it *knowledge*. What the user said three turns ago, which order they were asking about, what preferences they stated — information produced by the interaction itself — is *memory*, and this lab has none: run it twice and the second run knows nothing about the first. Memory is Milestone 5.

## RAG vs Fine-Tuning

RAG provides information at inference time; fine-tuning changes model weights through training. Documentation, policies, and application data change too often to be baked into weights just to make them retrievable — retrieval keeps them current and citable. Fine-tuning earns its cost only for stable behavioral change, and it is not implemented here.

## What we STILL do not have

- No memory — nothing persists across runs
- No skills — no procedure tells the model how to work
- No agent runtime — no loop, no autonomous steps
- No MCP — everything is local
- No vector database and no embedding model
- No reranker
- No autonomous retrieval loop — the application retrieves once, before the single model call
- No multi-agent system

## When should I use RAG?

- Internal documentation, manuals, policies, runbooks
- Large collections of mostly unstructured text
- Textual knowledge that changes too often to retrain into a model
- Any answer that should be grounded in, and attributable to, specific documents

## When should I NOT use RAG?

- An exact database lookup answers the question
- A deterministic API query answers the question
- The needed context is tiny and already available — just put it in the prompt
- Simple text transformation with no external knowledge
- Facts that must come from an authoritative structured system and can be queried directly

## Failure modes

RAG does not guarantee correctness. Three things can independently go wrong:

1. **Retrieval failure** — the right document was not selected; the model answers from the wrong context or none.
2. **Context failure** — the source itself is incomplete, stale, or wrong; the model faithfully grounds an answer in bad data.
3. **Generation failure** — the model misreads or ignores the retrieved content.

Retrieved context is not guaranteed truth, and RAG does not eliminate hallucination — it narrows the space. Measuring this properly is the Evaluation milestone.

## Production considerations

Not implemented here, deliberately: chunking strategy, indexing, embeddings and vector stores, hybrid retrieval, metadata filters, access control on documents (retrieval must respect who is asking — the model is never the authorization layer), document freshness, reranking, citations and provenance, prompt injection carried by retrieved content, context-window limits, and retrieval evaluation.

## What limitation remains?

The application can now bring external knowledge to the model for the current request. But it still remembers nothing from previous interactions — every run starts from zero. That leads to **Milestone 5 — Memory**, which is not implemented here.

## Sources consulted

Verified against official documentation during this milestone series (2026-09):

- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — `Client` construction and `client.models.generateContent(model, prompt, config)`; the same SDK and usage as Labs 01–03.
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — version 1.72.0, kept consistent with the earlier labs.
- [Gemini API model catalog](https://ai.google.dev/gemini-api/docs/models) — `gemini-3.8-flash` remains the recommended stable model for new projects.

No embeddings API and no retrieval library are used, so no further provider surface applies to this lab.
