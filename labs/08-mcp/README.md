# Lab 08 — MCP

**MCP does not create the agent. The agent already existed in Lab 07.**

**MCP standardizes how the application reaches a capability it does not own.**

**Discovery is not permission.**

## The limitation we found

Lab 07 ended with a working agent: a bounded runtime, a model that proposes the next step, and two read-only tools — `getServiceStatus` and `getRecentDeployment` — that were plain Java methods in the same JVM.

That worked because the application owned both capabilities. Now change one fact. The deployment records belong to the (fictional) deployment team. They live in the deployment team's process, not in ours. The incident agent can no longer call `ServiceTools.getRecentDeployment(...)`, because there is no such method in its process any more.

The question for this lab is not "how do I build an MCP server?" It is:

> We already have a working agent runtime and local Java tools. What changes when a capability lives outside the application boundary?

## Learning goals

- See what changes — and what does not — when one tool moves into another process
- Try the naive answer (custom client glue per capability) and see where its friction comes from
- Build the smallest useful MCP server with the official Java SDK: one tool, a clear input schema, validation, deterministic structured data
- Connect with the official Java client: initialize, discover, allowlist, call, translate
- Keep the Lab 07 runtime authoritative: the model proposes, the runtime validates and routes, the application decides
- Tell Tool, MCP, MCP server, MCP client, agent, and agent runtime apart
- Decide when MCP is justified — and when a method call or an ordinary API client is the better answer

## Starting point

The same fictional Helio incident, the same goal, the same bounded loop, the same budget of 4 model decisions, and the same two tool names the model can propose:

```
                     Lab 07                     Lab 08
getServiceStatus     local Java method          local Java method (unchanged)
getRecentDeployment  local Java method          MCP server, separate process
```

> "Investigate the notifications service. If it is degraded, check whether there was a recent deployment and summarize what is known. Do not claim a root cause without evidence."

Same agent. Same goal. Same bounded loop. One capability is now remote.

## Experience: local tools are enough

Look at Lab 07's tool boundary once more:

```java
return switch (request.name()) {
    case "getServiceStatus" -> ServiceTools.getServiceStatus(serviceName);
    case "getRecentDeployment" -> ServiceTools.getRecentDeployment(serviceName);
    default -> throw new IllegalArgumentException("... not on this application's allowlist.");
};
```

While the data lives in the application, this is the right design. A method call is the cheapest, fastest, most testable integration there is: no process to start, no protocol to negotiate, no network to fail, no second owner. Nothing in this lab changes that for `getServiceStatus` — it stays a local method, and it should.

**A method in the same process never needs MCP.**

## New constraint: the capability moves outside the process

When `getRecentDeployment` moves to the deployment team, three things become true that were not true before:

1. **Someone else owns it.** The deployment team decides what their service offers, and can change it without asking us.
2. **A call can fail in new ways.** The other process may not start, may answer with a protocol error, may report that the tool failed, or may return something the application cannot use. A local method could only reject bad input.
3. **The application needs a contract.** What is offered, what the inputs are, what a result looks like, and how errors are reported — none of that is visible in a method signature any more.

The agent loop does not care about any of this. The application does.

## Naive approach: custom client glue

The first reflex is to write a small client for this one service:

```java
// Hand-written glue, one per external capability
HttpResponse<String> response = http.send(
        GET("https://deployments.internal/api/v2/recent?svc=" + serviceName), ofString());
if (response.statusCode() == 404) { /* unknown service? or no deployment? */ }
Map<String, Object> deployment = parseTheirJson(response.body());
```

For **one** stable service used by **one** application, this is fine — and often the best answer. Keep it.

The friction appears as the count grows. Every capability owner invents an endpoint shape, a parameter style, an error format, and a way (or no way) to find out what is offered. Every consuming application — this agent, the team's other assistants, an IDE, a chat client — writes and maintains glue for every service it uses, and hand-copies descriptions into its model prompts. None of the glue is hard. There is just a lot of it, and none of it is shared: *what is offered, with which inputs, and how failures look* is different every time.

That is the specific friction a standard protocol can remove. It is not "HTTP is bad", and it is not "APIs are obsolete".

## What MCP actually solves

The [Model Context Protocol](https://modelcontextprotocol.io/specification/2025-11-25) is a standardized, application-facing protocol for exposing model-adjacent capabilities — tools, and also resources and prompts — to compatible clients. A server describes what it offers in a machine-readable way; any compatible client can connect, negotiate, discover, and invoke with the same messages and the same error model.

| Concern | Custom glue | MCP (as used in this lab) |
| --- | --- | --- |
| What is offered? | Documentation, if any | `tools/list`: name, description, input and output JSON Schema |
| How do I call it? | A per-service endpoint | `tools/call` with `{name, arguments}` |
| How do failures look? | Per service | JSON-RPC protocol errors vs tool execution errors (`isError`) |
| Which protocol version? | Ad hoc | Negotiated in the `initialize` handshake |
| How do I connect? | Per service | STDIO (this lab) or Streamable HTTP |

In the spec's vocabulary, the **host** is the application that coordinates everything — here, the incident agent. It creates one **client** per **server**. The spec puts connection permissions, security policy, and authorization decisions with the host. That matters below.

What MCP does **not** do:

- It does not decide which tools your application may use.
- It does not authorize the model, bound the loop, or stop the run.
- It does not make a remote capability trustworthy.
- It does not create an agent. Neither the server nor the client has a goal or a loop.

## Build the MCP server

[`server/DeploymentMcpServer.java`](src/main/java/dev/agentic/handbook/labs/mcp/server/DeploymentMcpServer.java) is the deployment team's process. It exposes exactly one tool over STDIO and nothing else — no resources, no prompts, no sampling, no elicitation, no roots, no completions. The protocol supports all of those; this lab teaches one boundary well instead of surveying the protocol.

The published contract:

```java
static final Map<String, Object> INPUT_SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of("serviceName", Map.of(
                "type", "string", "minLength", 1, "maxLength", 64,
                "description", "The name of a Helio platform service, for example \"notifications\".")),
        "required", List.of("serviceName"),
        "additionalProperties", false);
```

The server:

```java
McpServer.sync(new StdioServerTransportProvider(json))
        .serverInfo("helio-deployments", "1.0.0")
        .capabilities(ServerCapabilities.builder().tools(false).build())
        .tools(recentDeploymentTool(json))
        .build();
```

Input is validated twice, both times **on the server**:

1. **Shape** — the SDK validates every `tools/call` against the input schema before the handler runs. A missing, non-string, empty, or extra argument never reaches the handler; the caller gets a tool execution error that says why.
2. **Domain** — the handler checks what only the deployment team knows: whether the service exists. `payments` is not a known service, so the answer is `isError: true` with the reason. It is not an empty success, and it is not a made-up record.

A successful result carries `structuredContent` (checked by the SDK against the tool's published output schema) plus the same JSON as text, as the spec recommends. The data is fictional, deterministic, and in memory: no database, no network, no API key.

Two rules that are easy to miss:

- **Standard output belongs to the protocol.** Over STDIO, the server must not write anything to stdout that is not an MCP message. Its one human-readable line goes to stderr.
- **Annotations are hints.** The tool says `readOnlyHint: true`. The spec tells clients to treat annotations from untrusted servers as untrusted, and this application bases no decision on them.

The server package does not import anything from the agent application, and the application imports nothing from the server. A test enforces it. They share a Maven module only so the lab runs with one command.

## Discover and call the capability

[`DeploymentMcpClient.java`](src/main/java/dev/agentic/handbook/labs/mcp/DeploymentMcpClient.java) is the application's MCP client, and the only agent-side class that touches MCP SDK types. In order, it:

1. **Connects.** STDIO means the client starts the server as a child process and speaks JSON-RPC over its standard input and output:

   ```java
   ServerParameters server = ServerParameters.builder(javaExecutable())
           .args("-cp", System.getProperty("java.class.path"), SERVER_MAIN_CLASS)
           .build();
   McpSyncClient client = McpClient.sync(new StdioClientTransport(server, McpJsonDefaults.getMapper()))
           .clientInfo(Implementation.builder("helio-incident-agent", "1.0.0").build())
           .requestTimeout(Duration.ofSeconds(20))
           .enableCallToolSchemaCaching(true)
           .build();
   ```

2. **Initializes.** `client.initialize()` runs the handshake: protocol version and capabilities are negotiated. With SDK 2.0.1 the result is `2025-11-25`.
3. **Discovers.** `client.listTools()` returns what the server announces — names, descriptions, schemas.
4. **Decides.** It keeps only the tools on the application's allowlist:

   ```java
   static final Set<String> APPROVED_TOOLS = Set.of("getRecentDeployment");
   ```

   If an approved tool is missing, or its input contract no longer requires a string `serviceName`, the client fails **at startup**, before any model decision, instead of at the first call.
5. **Calls and translates.** The one approved call is a typed method, `getRecentDeployment(String)`. There is deliberately no generic `callTool(name, args)`: the only remote call this application can make is the one it approved and wrote. A successful structured result becomes a plain `Map` — the same kind of observation a local tool produces. Everything else becomes a `RemoteToolException` with the reason: a tool execution error, a protocol error, an unreachable server, or a "success" with no structured data. The application does not guess facts from free text.

**Discovery is not permission.** `tools/list` is information about what a server offers. The allowlist is the application's decision about what this runtime may use. If the deployment team adds `rollbackDeployment` to their server tomorrow, discovery will report it, and this agent will still never expose it: the test `announcedButUnapprovedToolsAreNeverExposed` starts a real server that announces exactly that, and asserts the tool stays invisible.

The live model (optional, below) is told about the allowed tools in the **application's own words**. The server's descriptions are not forwarded into the prompt: that text comes from another party, and putting it in front of the model is a trust decision of its own — tool-description injection is a known risk, and Milestone 11 covers it.

## Connect it to the bounded runtime

[`AgentRuntime.java`](src/main/java/dev/agentic/handbook/labs/mcp/AgentRuntime.java) is the Lab 07 runtime. The loop, the budget, and the stop rules are unchanged. The tool boundary changed in one line, plus one new way to stop:

```java
return switch (request.name()) {
    case "getServiceStatus" -> new ToolExchange(request, Route.LOCAL,
            ServiceStatusTool.getServiceStatus(requiredServiceName(request.arguments())));
    case "getRecentDeployment" -> new ToolExchange(request, Route.MCP,
            deployments.getRecentDeployment(requiredServiceName(request.arguments())));
    default -> throw new IllegalArgumentException("... not on this application's allowlist.");
};
```

The order is the point: the tool name must be on the switch, the arguments must validate, and only then does the application decide *where* the call runs. A rejected proposal never reaches the MCP client.

`ModelDecision.ToolRequest` did not change. The model names a capability; it does not know, and does not need to know, whether that capability is local or behind MCP. **The model does not speak MCP.** Where a tool runs is an infrastructure decision, and it belongs to the application.

```
Model
  ↓ proposes ToolRequest("getRecentDeployment", {serviceName})
Agent Runtime
  ↓ validates: allowlisted name, well-formed arguments, budget left
Application Tool Boundary
  ├── Local Java Tool        getServiceStatus
  └── MCP Client             getRecentDeployment
          ↓ tools/call over STDIO
       MCP Server            helio-deployments (separate process)
          ↓
    External Capability      the deployment team's records
```

The runtime remains authoritative over execution. MCP sits *below* the tool boundary, not above the runtime.

One new stop reason exists because remote calls can fail in ways a method could not: `TOOL_FAILED` — an allowed, valid request reached the MCP server and did not produce a usable result. The runtime stops with the reason. It does not retry, and it does not invent a result.

## Run it locally

Deterministic, no key, no hosted server, no network at run time — a scripted model drives the real runtime, and the runtime reaches a real MCP server in a real second process:

```sh
./mvnw -pl labs/08-mcp compile exec:exec
```

Windows: `.\mvnw.cmd -pl labs/08-mcp compile exec:exec`

Why `exec:exec` and not `exec:java` like the earlier labs: the application launches the MCP server as a separate `java` process with its own classpath. `exec:java` runs inside Maven's JVM, where that classpath does not exist; `exec:exec` runs the application as an ordinary `java -classpath ... McpExample` process, as a deployment would. (Typing `exec:java` out of habit fails with a message that says this.) The first run downloads the MCP SDK from Maven Central, like any dependency; after that nothing leaves your machine.

The scripted model is a test double, not an AI. It makes the same decisions as in Lab 07, so the only thing that changed is the thing this lab teaches.

## Run it with Gemini (optional)

```sh
./mvnw -pl labs/08-mcp compile exec:exec -Dexec.args="--live"
```

Requires `GOOGLE_API_KEY` (see [.env.example](.env.example)); `GEMINI_MODEL` overrides the default (`gemini-3.8-flash`). The same runtime, the same MCP connection, and the same budget apply. The live model may influence which allowed tool is proposed next. The application still owns the allowlist, the MCP server it connects to, validation, execution, the budget, and stopping. Neither the build nor CI needs the key.

## Read the execution trace

```
1. CONNECT: THE CAPABILITY NOW LIVES IN ANOTHER PROCESS
MCP server:              helio-deployments 1.0.0
Protocol version:        2025-11-25 (negotiated by the initialize handshake)
Announced (tools/list):  [getRecentDeployment]
Application allowlist:   [getRecentDeployment]
Exposed to the runtime:  [getRecentDeployment]

2. THE SAME BOUNDED RUNTIME, ONE CAPABILITY NOW REMOTE
Step 1 / 4
Model proposal:  getServiceStatus {serviceName=notifications}
Runtime route:   LOCAL  -> Java method in this process
Observation:     {serviceName=notifications, status=DEGRADED, message=...}

Step 2 / 4
Model proposal:  getRecentDeployment {serviceName=notifications}
Runtime route:   MCP    -> tools/call to helio-deployments (separate process)
Observation:     {serviceName=notifications, version=notifications-2.4.1, deployedAt=2026-09-23T13:52:00Z, ...}

Step 3 / 4
Model proposal:  FINAL ANSWER
Stop reason:       FINAL_ANSWER
MCP requests sent: 1

3. FAILURE: A TOOL THE APPLICATION NEVER APPROVED
Model proposal:  rollbackDeployment {serviceName=notifications}
Rejected:        Tool 'rollbackDeployment' is not on this application's allowlist.
Stop reason:       REJECTED_TOOL_CALL
MCP requests sent: 0

4. FAILURE: THE REMOTE CAPABILITY REPORTS AN ERROR
Model proposal:  getRecentDeployment {serviceName=payments}
Runtime route:   MCP    -> tools/call to helio-deployments (separate process)
Tool failed:     MCP tool error from 'helio-deployments': Unknown service 'payments'. ...
Stop reason:       TOOL_FAILED
MCP requests sent: 1
```

Read the two proposals in step 1 and step 2 side by side: they have the same shape. Only the **route** — chosen by the runtime — differs. "MCP requests sent" counts what actually crossed the process boundary: nothing, for a proposal the application refused.

This is educational output, not observability. Logs, metrics, and traces are Milestone 10.

## Failure boundaries

| What goes wrong | Who catches it | Result | Crossed the boundary? |
| --- | --- | --- | --- |
| Model proposes an unapproved tool (`rollbackDeployment`) | Runtime allowlist | `REJECTED_TOOL_CALL` | No |
| Malformed arguments (missing, extra, blank, non-string) | Runtime validation | `REJECTED_TOOL_CALL` | No |
| Server announces a tool the application did not approve | Client, at discovery | Never exposed | — |
| Approved tool missing, or its contract changed | Client, at startup | Fails before the first model decision | — |
| Arguments that violate the published schema (from any client) | Server (SDK schema validation) | Tool execution error | Yes |
| Unknown service | Server (domain validation) | Tool execution error → `TOOL_FAILED` | Yes |
| Handler crashes | Server → JSON-RPC protocol error | `TOOL_FAILED` | Yes |
| "Success" with no structured data | Client translation | `TOOL_FAILED` | Yes |

Every row is covered by a deterministic test against a real server process — the lab's own server, or a test server that misbehaves on purpose.

Nothing retries forever, guesses arguments, exposes discovered tools automatically, or turns an error into a fake success. The spec notes that clients *may* hand tool execution errors back to the model so it can self-correct; that is a reasonable later design with its own budget, but here a failure stays visible. Retries, backoff, circuit breakers, reconnection, and production timeouts are real concerns, and not this lesson — the 20-second request timeout exists only so a broken server cannot hang the lab.

## MCP vs Tool

A **tool** is a capability the application can execute on the model's behalf. Lab 03 introduced it; Lab 07 used two.

**MCP** is one way to *reach* a tool that someone else owns. In this lab, `getServiceStatus` and `getRecentDeployment` are both tools from the model's point of view. One is a method; one is a `tools/call` to another process. Tool calling (model proposes → application executes → result returns) is identical for both.

Tool ≠ MCP. Tool calling ≠ MCP. MCP is not required for local application code.

## MCP vs Agent

- **MCP server ≠ agent.** `helio-deployments` has no model, no goal, and no loop. It answers requests about data it owns.
- **MCP client ≠ agent runtime.** `DeploymentMcpClient` connects, discovers, and forwards one kind of call. It decides nothing about the next step, the budget, or when to stop.
- **MCP ≠ agent.** Remove MCP from this lab, put the method back, and the agent is exactly the Lab 07 agent. Add MCP to a single model call with no loop, and there is still no agent.

MCP does not create the agent. It changes how far the agent's tools can reach.

## MCP vs REST / custom API

REST, gRPC, and vendor SDKs are general-purpose integration mechanisms between applications and services. MCP is a standardized, application-facing protocol for exposing model-adjacent capabilities, resources, and prompts to compatible clients.

They are not competitors:

- A normal API client is often the simpler choice — one service, one consumer, a stable contract.
- An MCP server can sit **in front of** an existing REST or gRPC service. The deployment team's server here could just as well call their internal API instead of reading an in-memory map; the agent would not notice.
- MCP earns its place when *standardized discovery and invocation across compatible clients* solves a real integration problem — not because an application happens to contain an LLM.

MCP does not replace REST.

## Decision Authority

Connect this to [LLM vs Decision Authority](../../docs/model-vs-decision-authority.md). An MCP server exposing a tool does not make that tool executable by the model. Five parties, five different statements:

| Party | Says |
| --- | --- |
| MCP server | "I provide capability X." |
| MCP client | "I can communicate with that server." |
| Application | "I allow capability X for this runtime." |
| Model | "I propose using capability X." |
| Runtime | "I validate and decide whether the proposal becomes execution." |

Only the last two lines involve the model, and only the last one is binding. The application boundary remains authoritative — which is also where the MCP specification puts it: the host controls connection permissions and enforces security policy.

## Reflection

1. Which part of the system decided that `getRecentDeployment` runs over MCP?
2. Could the model have caused `rollbackDeployment` to run if the server had announced it? What stops it — and where is that written?
3. What is the difference between a tool execution error and a protocol error, and why does the runtime stop on both here?
4. Why does `getServiceStatus` stay a local method?
5. What would you gain and lose by forwarding the server's tool descriptions straight to the model?
6. If the deployment team runs a plain REST service and nobody else needs the capability, would you add MCP?
7. Did the agent change between Lab 07 and Lab 08?

Short guidance: the application's tool boundary (1); no — the allowlist is application code, and discovery cannot add to it (2); a tool error is the capability reporting failure, a protocol error means the request itself failed — both are failures, and neither may become a fake success (3); the application owns that data, and a method call is the simplest correct integration (4); convenience and one source of truth, against trusting another party's text in your prompt (5); probably not — one explicit client is simpler (6); no — only how far one tool reaches (7).

## Do I actually need MCP?

- **Same-process Java method?** Do not add MCP.
- **One stable internal service used by one application?** A normal API client may be simpler.
- **Need standardized discovery and invocation of capabilities across compatible clients or runtimes?** MCP may be justified.
- **Need another autonomous agent?** That is not MCP. A2A and multi-agent questions belong later.

```
Is the capability local?
├── Yes → use ordinary application code.
└── No  → Is one explicit API integration sufficient?
          ├── Yes → a normal client may be simpler.
          └── No  → Do you benefit from a standardized capability boundary
                    that compatible clients can discover and invoke?
                    ├── No  → keep the explicit integration.
                    └── Yes → MCP may be justified.
```

MCP is not automatically better than REST, gRPC, or an SDK client. If you do adopt it, decide before connecting: Which servers are trusted, and who operates them? Which announced tools are allowed, and who approves a new one? Which tools may run without a human? What happens when the server is unavailable?

## Implementation decisions

- **SDK: the official MCP Java SDK, used directly.** `io.modelcontextprotocol.sdk:mcp:2.0.1`, the latest GA release on Maven Central when this lab was written. No Spring Boot, no Spring AI, no LangChain4j — this lab teaches MCP, not framework integration. No SNAPSHOT, milestone, or release candidate.
- **Dependency: `mcp` rather than `mcp-core` + a JSON module.** `mcp` is exactly `mcp-core` plus its default Jackson 3 binding; `mcp-core` alone has no JSON implementation. One coordinate is the smallest complete surface. `slf4j-nop` (runtime scope) is added only so SLF4J does not print a "no provider" warning into the trace; SDK-internal logs are discarded on purpose.
- **Spec revision: 2025-11-25.** That is the revision SDK 2.0.1 implements (its newest negotiated version). The current MCP revision is **2026-07-28**, which makes the protocol stateless: it removes the `initialize` handshake in favour of per-request version metadata and a `server/discover` call. No GA Java SDK release implements it yet, and this lab does not chase the newest revision with a pre-release SDK. The lesson — discovery is not permission, and the application stays authoritative — is the same in both revisions; the handshake details are what will change.
- **Transport: STDIO.** It makes the process boundary concrete (the client really starts a second process), needs no HTTP server, opens no port, and needs no infrastructure. Streamable HTTP is the choice when the server is a network service; it brings authorization and origin validation with it, which belong to Milestone 11. SSE-only transport is deprecated and not used.
- **No ADR.** Using the official SDK directly is this lab's implementation choice, not a rule later work must follow: the ecosystem track explicitly welcomes framework-based MCP examples where they clarify a concept. The decision is recorded here instead.

## What we STILL do not have

- No evaluation of whether the agent behaves correctly
- No observability — the trace is printed text, not telemetry
- No authentication or authorization between client and server (STDIO inherits the parent's trust; network transports need real auth)
- No MCP resources, prompts, sampling, elicitation, roots, or completions
- No Streamable HTTP, no remote or hosted servers, no multiple servers, no server registry
- No retries, backoff, circuit breakers, or reconnection
- No side-effecting remote tools and no human approval flow
- No A2A and no multi-agent system

## What limitation remains?

After this lab, the system can run a bounded agent, use local tools, and reach an external capability through a standard protocol. Every piece has been shown to work — one deterministic path at a time.

But "every test passes" is not the same as "the system behaves correctly". Does the agent choose sensible steps? Does its final answer stay within the evidence? Would a model upgrade, a prompt change, or a new MCP server version make it worse — and would anyone notice? The labs so far have no systematic answer to:

> Does this system actually behave correctly?

That is **Milestone 9 — Evaluation**. Not implemented here.

## Sources consulted

Verified against official sources on 2026-09-29:

- [MCP specification 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25) — [architecture](https://modelcontextprotocol.io/specification/2025-11-25/architecture) (host, client, server; the host enforces security boundaries), [transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports) (STDIO: newline-delimited messages, stdout reserved for protocol messages, stderr for logs), and [tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) (`tools/list`, `tools/call`, input and output schemas, protocol errors vs `isError` tool execution errors, annotations as untrusted hints).
- [MCP versioning](https://modelcontextprotocol.io/specification/versioning) and the [2026-07-28 changelog](https://modelcontextprotocol.io/specification/2026-07-28/changelog) — 2026-07-28 is the current revision; it removes the `initialize` handshake and adds `server/discover`.
- [Official SDK list](https://modelcontextprotocol.io/docs/sdk) — the Java SDK is an official SDK (`modelcontextprotocol/java-sdk`).
- [modelcontextprotocol/java-sdk](https://github.com/modelcontextprotocol/java-sdk) — README (modules: `mcp-core`, `mcp-json-jackson2`/`jackson3`, `mcp` bundle; STDIO and Streamable HTTP) and [releases](https://github.com/modelcontextprotocol/java-sdk/releases) (2.0.1 latest; `main` is 2.1.0-SNAPSHOT).
- [MCP Java SDK documentation](https://java.sdk.modelcontextprotocol.io/latest/) — [client](https://java.sdk.modelcontextprotocol.io/latest/client/) (`StdioClientTransport`, `ServerParameters`, `McpClient.sync`) and [server](https://java.sdk.modelcontextprotocol.io/latest/server/) (`StdioServerTransportProvider`, `McpServer.sync`, `SyncToolSpecification`, input validation on by default).
- The SDK 2.0.1 sources (`mcp-core-2.0.1-sources.jar` from Maven Central) — `ProtocolVersions` (newest: `2025-11-25`), `ToolInputValidator`, and the unknown-tool and handler-exception error paths.
- [Maven Central: io.modelcontextprotocol.sdk:mcp](https://central.sonatype.com/artifact/io.modelcontextprotocol.sdk/mcp) — 2.0.1 is the latest release.
- For the optional live mode, the same Gemini sources as [Lab 07](../07-agent-runtime/README.md#sources-consulted); `google-genai` stays on 1.72.0.
