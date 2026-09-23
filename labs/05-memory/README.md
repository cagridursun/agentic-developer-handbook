# Lab 05 — Memory

**Conversation history is not memory.**

**Memory is application-controlled state.**

**Forgetting is part of memory design.**

This is still not an agent.

## The limitation we found

Lab 04 gave the application the ability to retrieve knowledge that exists outside the conversation — documentation that would be true whether or not anyone ever asked. But nothing yet survives *inside* the conversation. Run any earlier lab twice and the second run knows nothing about the first.

Say the user tells us:

> "My preferred programming language is Java."

and later asks:

> "What programming language do I prefer?"

If the second request does not contain information from the first turn, the model cannot know. Not because it is forgetful — because every model request is bounded by the context supplied with it. The model sees exactly what the application sends, every time, from zero.

## Learning goals

- See why an LLM does not remember previous interactions
- Distinguish conversation history (a transcript) from memory (selected state)
- Watch the naive full-history workaround succeed, then degrade
- Build explicit, application-controlled session memory
- Use the full lifecycle: remember, read, update, forget
- Decide when memory is justified at all

This lab pilots an experience-first format: you meet the limitation before you get the solution.

## Starting point

Everything below is one scripted, deterministic demonstration in `MemoryExample.main`. The learning is in the prompts the application builds — you can inspect all of them without an API key. Only the final memory-backed step optionally calls Gemini.

## Experience 1 — No memory

The application builds a prompt containing only the later question:

```
You are answering the user's current question.

Current question:
What programming language do I prefer?
```

The earlier fact is absent. Nothing "failed" — the application never carried it forward. This is the honest baseline of every LLM application: no context, no answer.

## Naive fix — Send the entire history

The obvious workaround: keep the transcript and send all of it, every time.

It works — the preference is in there. Then the demo grows the conversation the way real conversations grow: a failed staging deployment, lunch plans, meeting notes. Eight turns later:

```
Conversation turns:  8
History characters:  562   (the memory prompt below: 350)
```

The final question still needs exactly one small fact, but the prompt now carries a build failure and a lunch order, and it grows with every future turn, forever. It also carries whatever sensitive or stale content ever appeared. Carrying a transcript can be a valid source of context — but it is not the same engineering decision as designing memory.

History is the full meeting recording. Memory is the small set of notes you intentionally keep afterward.

## Build — Explicit session memory

The application extracts the one durable fact into state it owns:

```java
SessionMemory memory = new SessionMemory();
memory.remember("preferredProgrammingLanguage", "Java");
```

`SessionMemory` is a `LinkedHashMap` with a contract: `remember` stores or updates (blank keys and values are rejected), `get` reads, `forget` deletes, `snapshot` returns a deterministic, insertion-ordered view. When the JVM exits, it is gone — deliberately. Persistence is a storage choice, not the definition of memory.

The future prompt contains the question and the memory snapshot, nothing else:

```
You are answering the user's current question.

The application has retained the following memory from earlier interactions.

Memory:
<entry key="preferredProgrammingLanguage">
Java
</entry>

Current question:
What programming language do I prefer?

Use the memory only when it is relevant. If the memory does not contain
enough information, say so.
```

The `<entry>` tags are structure, not a security boundary. And notice who decided what became memory: the application, explicitly and deterministically. No model extracted it automatically — auto-extraction bundles trust, policy, and correctness problems that would bury this lesson.

Then the lifecycle: remembering `"Kotlin"` under the same key *replaces* Java — memory holds current state, not a history of states. And `forget("preferredProgrammingLanguage")` removes the entry from every future prompt. A memory system that can remember but cannot forget is incomplete.

## Run it

Works fully without an API key — the demonstration prints every prompt:

```sh
./mvnw -pl labs/05-memory compile exec:java
```

Windows (PowerShell):

```powershell
.\mvnw.cmd -pl labs/05-memory compile exec:java
```

With `GOOGLE_API_KEY` set (from [Google AI Studio](https://aistudio.google.com/apikey)), the explicit-memory step additionally makes one real Gemini call and prints the grounded answer. `GEMINI_MODEL` overrides the default (`gemini-3.8-flash`); variable names are in [.env.example](.env.example).

## Compare the three approaches

| | No memory | Full history | Explicit memory |
| --- | --- | --- | --- |
| Carries prior information? | No | Yes, all of it | Yes, selected |
| Carries unrelated turns? | No | Yes | No |
| Explicit lifecycle? | — | No, it only grows | remember / update / forget |
| Can forget selected state? | — | No | Yes |
| Scales with conversation length? | Yes (trivially) | No | Yes |

This is a teaching comparison for this scenario, not a production benchmark. Real systems often combine a *bounded* recent-history window with explicit memory.

## Conversation history is not memory

History answers *"what happened?"* — every turn, in order, relevant or not, growing forever. Memory answers *"what state should survive?"* — selected deliberately, scoped deliberately, updated deliberately, forgotten deliberately, and included in future context only when useful.

Key-value entries are just the simplest possible implementation for this lab. Real memory is not necessarily key-value — the defining property is application-controlled lifecycle, not the data shape.

## Memory vs Knowledge / RAG

Knowledge (Lab 04) asks: *what external information does the model need?* — "the Helio platform uses 45-minute access tokens" is true regardless of who is asking, owned by the documentation.

Memory asks: *what should persist from earlier interaction?* — "this user prefers Java examples" originated in a conversation and is retained for future ones.

The same storage technology could theoretically hold both. That does not make them the same concept: they differ in ownership, lifecycle, and update semantics, and the distinction is architectural, not technological.

## Memory vs authoritative lookup

If the real value already lives in an authoritative system — the user's language preference in a profile service, the selected project in the application database — query that system. It is simpler, always current, and consistent across devices and sessions. Memory earns its place for information that has no authoritative home other than the interaction itself.

## Memory vs model training

Memory is application state: it changes immediately, can be forgotten, can be scoped per user or session, and is supplied at inference time. Fine-tuning changes model weights through training — slow, global, and effectively unforgettable. A model's weights are not a per-user database, and mutable user state should never be trained in just to make it available.

## What should be remembered?

Good candidates: explicit user preferences, the selected working context, durable choices needed in later turns, workflow state the application must resume.

Bad candidates: every message, temporary noise, secrets and credentials, authorization decisions, stale assumptions, and facts already available from an authoritative system.

Memory should be selective. "Store everything forever" is not a memory strategy — it is a liability with a retention policy of never.

## Forgetting is part of memory

Preferences change. Facts go stale. Users ask for deletion. Workflows end. Incorrect memories need correction. In this lab, `forget(key)` is explicit and immediate; production systems add TTLs, retention policies, deletion workflows, audit trails, and provenance tracking. Forgetting is not a nice-to-have on top of memory — it is half of the design.

## Scope matters

This lab implements exactly one scope: one session in one process. Production memory may be scoped per request, conversation, session, user, team, tenant, or organization — and a memory read that crosses those boundaries is a data leak, full stop. One user's remembered preferences must never surface in another user's prompt. Scoping is a design decision to make before choosing any storage.

One more boundary that already applies: memory is context, never authority. Remembering "I am an administrator" must grant nothing. Authorization belongs to application security controls, and the model is never the authorization layer.

## Reflection

1. Which information in the demonstration actually needed to survive?
2. Why is sending the whole transcript different from designing memory?
3. Who decided what became memory: the model or the application?
4. What could go wrong if every user message were remembered forever?
5. What information should your own application deliberately forget?
6. Could the same persistence technology store both knowledge and memory? If so, why are they still different concepts?
7. Would this use case still need memory if the authoritative value could be fetched from a normal API?

Short guidance: one fact needed to survive (question 1); the transcript carries noise, cost, and risk without a lifecycle (2); the application decided, which is the point of the lab (3); unbounded retention accumulates stale, sensitive, and wrong state (4–5); shared storage does not merge distinct ownership and lifecycle semantics (6); and if an authoritative source exists, prefer it (7).

## Do I actually need memory?

```
Does anything from an earlier interaction need to affect a later request?
├── No  → You probably do not need memory.
└── Yes → Can the value be fetched from an authoritative source instead?
    ├── Yes → Prefer querying the authoritative source.
    └── No  → Is the information worth deliberately retaining?
        ├── No  → Do not store it.
        └── Yes → Memory may be justified.
```

If memory is justified, five questions remain before any code: How long should it live? Who owns it? Who can read it? How can it be updated? How can it be forgotten?

You probably don't need all of these — and many applications genuinely do not need memory.

## What we STILL do not have

- No durable storage — the JVM exits, the memory is gone, by design
- No automatic memory extraction — the application decides, not the model
- No semantic memory retrieval — the snapshot is small enough to include whole
- No skills
- No agent runtime
- No MCP
- No autonomous loop
- No multi-agent system

## Production considerations

Not implemented here, deliberately: durable persistence, user/session/tenant isolation, TTL and retention, encryption, privacy and consent, deletion workflows, memory provenance, stale and conflicting memories, summarization, relevance selection, semantic retrieval, multi-device access, concurrency, distributed storage, memory poisoning, prompt injection carried by stored memory, and auditing.

The common thread: production memory is primarily an application data-management problem. There is no magic inside the LLM — the model just reads whatever context the application constructs.

## What limitation remains?

We now have a model, structured output, tools, knowledge, and memory. But every lab still encodes *how to perform a task* directly in its prompt, and would encode it again next time. Reusable task procedures — instructions the application can hand to a model for a class of work — are the next concept: **Milestone 6 — Skills**. Not implemented here.

## Sources consulted

Verified against official documentation during this milestone series (2026-09):

- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — `Client` construction and `client.models.generateContent`; the same SDK usage as Labs 01–04.
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — the labs stay on 1.72.0 for consistency across the canonical path; 1.73.0 was released during this milestone and an upgrade would be coordinated across all labs, not done piecemeal.
- [Gemini API model catalog](https://ai.google.dev/gemini-api/docs/models) — `gemini-3.8-flash` remains the recommended stable model.

Memory itself uses no provider feature: it is application state and prompt construction.
