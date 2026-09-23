# Lab 03 — Tools

**The model does not execute the tool. Your application does.**

This is a controlled tool-calling flow, not yet the Agent Runtime.

## The limitation we found

Lab 01 could produce text. Lab 02 could produce structured data. Both worked entirely from the model's trained weights plus whatever the prompt contained.

Neither could access current application state. Ask "what is the status of the authorization service right now?" and the model has two options: admit it doesn't know, or invent an answer. Your application knows the real status. The model has no way to reach it.

## What problem are we solving?

The user asks for information that belongs to the application, not the model. We need a way for the model to say "I need `getServiceStatus("authorization")` to answer this" — and for the application to perform that lookup and hand the result back.

## What are we building?

```
User
 ↓
Java Application
 ↓
Gemini  (with the tool declaration)
 ↓
Tool Call Request: getServiceStatus({"serviceName": "authorization"})
 ↓
Java Application  (validates the request)
 ↓
ServiceCatalog.getServiceStatus("authorization")
 ↓
Tool Result: { serviceName, status: HEALTHY, message }
 ↓
Gemini  (receives the result)
 ↓
Final Answer to the user
```

The tool implementation is a deterministic in-memory catalog of four services — `authorization`, `notifications`, `billing`, `search` — with fixed states. Deliberately boring: the point of this lab is the boundary, not the tool.

## Run it

You need Java 21 and a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).

Linux/macOS:

```sh
export GOOGLE_API_KEY=your-key
./mvnw -pl labs/03-tool-calling compile exec:java
```

Windows (PowerShell):

```powershell
$env:GOOGLE_API_KEY = "your-key"
.\mvnw.cmd -pl labs/03-tool-calling compile exec:java
```

The default question is "What is the current status of the authorization service?". Ask your own:

```sh
./mvnw -pl labs/03-tool-calling compile exec:java -Dexec.args="Is the search service up?"
```

`GEMINI_MODEL` overrides the default model (`gemini-3.8-flash`). Variable names are in [.env.example](.env.example).

## The three important objects

1. **Tool declaration** — what the model is told exists: a name (`getServiceStatus`), a description, and a parameter schema (`serviceName: string`, required). It travels with the request. It contains no code.
2. **Tool call request** — what the model sends back instead of an answer: the name of a declared tool and JSON arguments. It is a *proposal*, not an invocation.
3. **Tool result (function response)** — what the application sends after executing the capability: a structured payload the model uses to write the final answer.

## Who executes the tool?

The model generates a request. That is all it can do.

The Java application decides whether to execute it. The model has no handle to `ServiceCatalog`, no classpath, no reflection, no way to reach the JVM. If the application ignored the request, nothing would happen. In `ToolCallingExample.main` there is a marked line where model decision ends and application execution begins — everything after it is ordinary Java that you could step through in a debugger.

**The model proposes an action. The application authorizes and executes it.**

## Tool declaration vs Java method

What the model knows:

```
name:        getServiceStatus
description: Returns the current operational status of a known platform service.
parameters:  { serviceName: string, required }
```

What the application owns:

```java
public static Optional<ServiceStatus> getServiceStatus(String serviceName) {
    // a Map lookup — normal Java, no model anywhere
}
```

The declaration is an interface published to the model. The implementation is private to the application. The model cannot see the method, call the method, or discover other methods.

## Tool calling step by step

Following `ToolCallingExample.main` from top to bottom:

1. **Declare** — `FunctionDeclaration` for `getServiceStatus` is attached to `GenerateContentConfig` via a `Tool`. Declared manually: the SDK's automatic function calling (passing reflected `Method` objects) is deliberately not used, because it would execute the tool inside the SDK and hide the exact boundary this lab exists to show.
2. **Ask** — the user's question goes to `client.models.generateContent(...)`.
3. **Receive** — `response.functionCalls()` contains the model's request, e.g. `getServiceStatus({serviceName=authorization})`. If it's empty, the model answered directly and the program simply prints that.
4. **Inspect and validate** — `handleToolRequest(name, args)` checks the name against the allowlist and validates every argument. This is the trust boundary.
5. **Execute** — `ServiceCatalog.getServiceStatus(...)`: a Java method call.
6. **Respond** — the result map goes back as `Part.fromFunctionResponse(...)`, appended to the conversation together with the model's own tool-request turn.
7. **Second model call** — the model now sees question → its request → the result, and produces the human-readable final answer.

The second call matters: the Java function returns application data (`DEGRADED`, "Email delivery is delayed…"); the model turns that data into an answer for the user. Formatting the final answer in Java would skip the concept being taught — the full model → application → model cycle.

## Trust boundary

The tool name and every argument in a tool call request were generated by a model, which makes them untrusted input — exactly like a request parameter from the internet. Before any Java method runs, `handleToolRequest`:

- rejects any tool name that is not `getServiceStatus` (the application owns the allowlist; the model cannot choose arbitrary capabilities)
- rejects missing, blank, or non-string `serviceName` values
- rejects unexpected extra arguments
- never resolves the tool name through reflection or dynamic dispatch

One deliberate distinction: a *malformed request* (unlisted tool, bad arguments) is an application-level failure and throws. A *well-formed request for an unknown service* is not — the tool result carries an error message and the list of known services, and the model relays that to the user. Fail safely; don't guess what the model meant.

## Tool vs API

An API is an interface between applications or services. A tool is a capability exposed *to a model*. A tool implementation may internally call Java code, an HTTP API, a database, a filesystem, or another service — the model neither knows nor cares. Here it's a `Map` lookup so the boundary is easy to see.

The direction matters: you don't turn every API into a tool. You choose the few capabilities the model legitimately needs, declare those, and nothing else.

## Tool vs MCP

This lab connects a model to a local Java method:

```
Model → tool declaration → local Java method
```

No protocol is needed for that, so no MCP is used. MCP earns its place when capabilities or resources must be exposed or consumed across a standardized protocol boundary — a separate server offering tools to many clients, or an application consuming tools it doesn't own. Introducing MCP to call a method in the same JVM is exactly the kind of unnecessary complexity this handbook warns against. Milestone 8 covers MCP properly.

## What we STILL do not have

- No general agent runtime — the flow is hardcoded for this one interaction.
- No autonomous execution loop — no `while`, no "run until done".
- No multiple tool rounds — a second request after the result is rejected, with a message pointing to the Agent Runtime milestone.
- No RAG — nothing is retrieved into the prompt.
- No memory — a second run knows nothing about the first.
- No skills — no procedure tells the model how to work.
- No MCP — the tool is a local method.
- No multi-agent system — there is one model and one application.

## Is this an agent?

Terminology varies. Parts of the industry would call this lab an agent already: a model that decides to use a tool, gets the result, and answers. That usage is common, and this handbook does not claim it is universally wrong.

For the purposes of this handbook, this example is treated as a controlled model + tool interaction. The application permits exactly one tool round, chose the flow in advance, and stops. "Agent Runtime" is reserved for Milestone 7, where the application owns a generalized execution loop with multiple possible steps, limits, and explicit stopping rules. The reason for the strict vocabulary is educational: when every model call is called an agent, the word stops carrying information.

## Structured output and tool calling solve different problems

- Structured output (Lab 02): *how should the model's response be shaped?*
- Tool calling (Lab 03): *how can the model request that the application perform a capability?*

They compose naturally — a tool's arguments and results are structured data by necessity — but they are different concepts, and this lab deliberately keeps its final response as plain text so the new concept stands alone.

## When should I use a tool?

- The answer depends on current application state (this lab)
- A calculation the model should not approximate
- A database or catalog lookup
- An external API operation
- An application action: create a ticket, send a notification — with authorization checks in the application

## When should I NOT use a tool?

- The model already has enough context in the prompt to answer
- Simple text transformation — summarize, rewrite, translate
- Static instructions that could just be part of the prompt
- Information already present in the conversation

Every declared tool enlarges the model's decision space and your attack surface. Declare the minimum.

## Production considerations

Not implemented here, deliberately:

- **Authentication** — the application authenticates to downstream systems; the model never holds credentials.
- **Authorization** — the tool must enforce *who may do this*, per user, in application code. The model is never the authorization layer.
- **Argument validation** — this lab shows the minimum; real systems validate against business rules too.
- **Timeouts** — a tool that hangs stalls the whole exchange.
- **Idempotency** — models occasionally repeat requests; executing twice must be safe or detected.
- **Retries** — transient tool failures need a policy, and retrying a non-idempotent action is dangerous.
- **Destructive operations** — anything that deletes, pays, or sends usually needs confirmation or human approval, not just an allowlist entry.
- **Auditability** — log every proposed call, every validation rejection, and every execution with its arguments.

## What limitation remains?

The flow is manually hardcoded for one tool round: one question, at most one tool call, one final answer. A larger system eventually needs execution control — which tool, how many rounds, when to stop. That is the Agent Runtime, and it comes in Milestone 7.

Before that, the handbook separately introduces Knowledge / RAG (Milestone 4), Memory (Milestone 5), and Skills (Milestone 6). Each is one concept, in order. No skipping ahead.

## Sources consulted

Verified on 2026-09-23 against official documentation:

- [Gemini API function calling guide](https://ai.google.dev/gemini-api/docs/function-calling) — declaration structure (name, description, parameter schema), the request/execute/respond cycle, and sending function results back to the model.
- [googleapis/java-genai README](https://github.com/googleapis/java-genai) — manual function declarations via `FunctionDeclaration`/`Tool`/`GenerateContentConfig`, and the automatic function calling mode (reflected `Method` objects) that this lab deliberately avoids.
- [java-genai Javadoc: `GenerateContentResponse`](https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/GenerateContentResponse.html) — `functionCalls()` accessor; [`Part`](https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/Part.html) — `fromFunctionResponse(name, response)` for returning results.
- [Maven Central: com.google.genai:google-genai](https://central.sonatype.com/artifact/com.google.genai/google-genai) — 1.72.0, the same version as the earlier labs.
