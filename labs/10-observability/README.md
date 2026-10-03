# Lab 10 — Observability

**Logs tell you individual events. Metrics tell you aggregate behavior. Traces tell you how events belong to one run.**

**Evaluation asks whether the system behaves as intended across cases. Observability asks what actually happened during this run.**

**"Observable" does not mean "record every byte."**

## The problem

Lab 09 ended with an agent we can evaluate: a bounded runtime, two read-only tools, a small set of cases, and checks on both the final answer and the trajectory. Evaluation gives controlled evidence about quality, on cases we chose in advance.

Now a real request arrives that is not one of our cases, and the run fails. A developer needs to answer questions no evaluation set answers:

- Which model was called, and how many times?
- Which decision did the agent make at each step, and which tool did it propose?
- What did the application allow, and what did the tool return?
- How long did each part take?
- Where did the run stop, and why?
- Can I reconstruct the execution path **after the fact**?

> Even if we can evaluate the behavior of an agent in controlled scenarios, what happens when a real run fails?

## Learning goals

- Tell **logs**, **metrics**, and **traces** apart, and know what each can and cannot answer
- Model one agent run as a **trace**: one **correlation id**, nested **spans** with parent/child relationships
- **Instrument** model calls and tool calls without changing what the runtime does
- Record **why a run stopped**, and the information a failed span needs to be diagnosed
- Keep **metrics** few, and see why they summarize while traces explain
- **Diagnose** a realistic failure from a trace
- See the **decision-authority boundary** (model proposes, application validates, tool executes) in the trace
- Know what must **not** be recorded, and where that question becomes the next milestone
- Decide whether you need a tracing backend at all

## Starting point

The same fictional Helio platform and the same bounded agent as Lab 09: a budget of 4 model decisions, two read-only tools (`getServiceStatus`, `getRecentDeployment`), and the goal you already know:

> "Investigate the notifications service. If it is degraded, check whether there was a recent deployment and summarize what is known. Do not claim a root cause without evidence."

The lab carries its own copy of the runtime, as Labs 08 and 09 do — educational duplication, no imports from other labs. The tools are local Java methods, on purpose: the same spans would wrap an MCP-backed call (see "MCP continuity"). The runtime's loop, budget, and allowlist are unchanged; instrumentation was added around them.

## Experience the failure

Run the lab and look at the first thing it shows (section 1 of the output). A run failed. This is everything an operator, and a Lab 09 style check, can say about it:

```
What an operator would see in a plain log:
  WARN agent run did not complete (MAX_STEPS)

What a Lab 09 style evaluation says about it:
  [FAIL] stopped for the intended reason (FINAL_ANSWER)   stop reason was MAX_STEPS
  [FAIL] a final answer was produced                      no answer
  [PASS] stayed within the step budget                    4 model decision(s), budget 4

We know that it failed. We do not know what the run did, or why.
```

The run **failed**. The plain log says `did not complete (MAX_STEPS)`. The Lab 09 style checks say the stop reason was wrong and there is no answer. Both are true, and neither says what the run actually did. Was the model stuck? Was a tool slow? Did the application refuse something? Did the budget just happen to be too small?

> "The agent did not complete." — and nothing else to go on.

## What do we actually know?

Only that the run stopped, why *in one word*, and that it used its budget. We do not know:

- which tools were requested, in what order, with which arguments
- what the tools returned
- whether the application allowed everything the model proposed
- where the time went

The runtime knew all of this while it ran. It threw it away.

## Naive logging

The reflex is to add print statements (illustrative, not part of the lab's code):

```java
System.out.println("calling tool " + request.name());
System.out.println("got result " + result);
System.out.println("agent finished");
```

That is better than nothing, and enough for a throwaway script. It stops working for an agent, for the reasons in the next section.

## Why logs alone are insufficient

- **No correlation.** Two runs at once interleave their lines. Which `got result` belongs to which request?
- **No structure.** `calling tool getRecentDeployment` has to be parsed by a person, every time. You cannot ask "how many tool calls failed?" without regular expressions.
- **No relationships.** A flat sequence cannot say that a tool result belongs to *that* tool call, or that a tool call was proposed by *that* model decision.
- **No context.** Which user request? Which model? Which step?
- **Nothing about time or cost** unless you remembered to print it, in a format you remembered to keep.

A log line is an event. Debugging an agent needs to know how events belong together. Printing strings is not observability: useful observability needs consistent structure, correlation, context, timestamps and durations, relationships, and meaningful dimensions.

## Logs vs metrics vs traces

They are three different tools, not three formats of the same thing. For this agent:

| | Question it answers | Example from this lab |
| --- | --- | --- |
| **Log** | What happened, at one moment? | `event=tool_call tool=getServiceStatus status=OK` |
| **Metric** | How much, how often, across runs? | `agent.tool.calls = 8` |
| **Trace** | How did these events belong to one run? | `run-001`: model call, proposal, tool call (validation, execution), model call, proposal, tool call, ... |

A trace is particularly useful for an agent, because one user request produces a chain of model decisions, tool calls, observations, and further decisions. The order and the nesting *are* the explanation.

OpenTelemetry's documentation describes the same three signals in similar terms: a log is a timestamped text record, structured or not, with optional metadata; a metric is a measurement of a service captured at runtime; and a trace is the path of a request through an application, made of spans. It notes that logs "aren't enough for tracking code execution" on their own, because they lack context such as where they were called from, and become far more useful when correlated with a trace and a span. The definitions in this section are this handbook's teaching; the documentation is cited as support, not as the source of the lab's design.

## Model the agent run as a trace

One user request is one **run**. The run is one **trace**, and the trace is a tree of **spans**. Each span is a unit of work: what it was, which span it belongs to, when it started and ended, how it ended, and a few attributes.

```
TRACE run-001
AGENT_RUN
├── MODEL_CALL                      the model is called
│   └── AGENT_DECISION              ... and proposes something
├── TOOL_CALL                       one proposed tool call, as the application handled it
│   ├── TOOL_VALIDATION             the application: allowed? valid arguments?
│   └── TOOL_EXECUTION              the tool ran; this is its result
├── MODEL_CALL
│   └── AGENT_DECISION
...
└── FINAL_RESPONSE                  the answer, if there was one
```

The model is in [`Span`](src/main/java/dev/agentic/handbook/labs/observability/Span.java), [`SpanType`](src/main/java/dev/agentic/handbook/labs/observability/SpanType.java), [`SpanStatus`](src/main/java/dev/agentic/handbook/labs/observability/SpanStatus.java), [`Trace`](src/main/java/dev/agentic/handbook/labs/observability/Trace.java), and [`TraceRecorder`](src/main/java/dev/agentic/handbook/labs/observability/TraceRecorder.java). A span has:

- `traceId`: the correlation id, shared by every span of the run
- `spanId` and `parentSpanId`: the nesting (the root has no parent)
- `type` and `name`
- `start`, `end`, and a derived `duration`
- `status`: `OK`, `REJECTED` (the application refused a proposal), or `ERROR` (something failed, or the run did not reach its goal)
- `attributes`: a few string key/values, such as `model.name`, `result`, `error.type`, `error.message`, `stop_reason`

That is all. There are seven span types because those are the ones needed to answer "what happened between the request and the answer?". The tool *result* is an attribute of the `TOOL_EXECUTION` span, so it belongs to its own tool call by construction.

## Add correlation

Every span the recorder creates carries the run's trace id. Every log line carries the trace id *and* the span id of the span it belongs to:

```
  2026-10-01T15:58:56.211093563Z INFO trace=run-001 span=span-004 event=tool_call tool=getServiceStatus status=OK duration_ms=2
  2026-10-01T15:58:56.215088677Z INFO trace=run-001 span=span-009 event=tool_call tool=getRecentDeployment status=OK duration_ms=0
  2026-10-01T15:58:56.215851977Z INFO trace=run-001 span=span-014 event=tool_call tool=getRecentDeployment status=OK duration_ms=0
  2026-10-01T15:58:56.216424141Z INFO trace=run-001 span=span-019 event=tool_call tool=getRecentDeployment status=OK duration_ms=0
  2026-10-01T15:58:56.217053897Z ERROR trace=run-001 span=span-001 event=run_end stop_reason=MAX_STEPS steps=4 duration_ms=19
```

Without correlation: "I have many logs." With correlation: "I can reconstruct one execution." Given `trace=run-001`, you can filter every log line to one run and look up `span-009` in the trace to see what that line was about.

Ids in this lab are sequential (`run-001`, `span-001`) so the output is easy to read and to compare with this README. They are unique within one `Telemetry` instance, which is all one process needs. A system with more than one process generates random ids, and that is exactly where **context propagation** starts to matter (see "Optional production tooling").

## Instrument model calls

[`AgentRuntime`](src/main/java/dev/agentic/handbook/labs/observability/AgentRuntime.java) starts a `MODEL_CALL` span before each model call and ends it afterwards. The model's proposal becomes an `AGENT_DECISION` child span: either `propose getRecentDeployment(serviceName=notifications)` or `propose a final answer`. If the model call throws, the span ends in `ERROR` with the exception's type and message, and the run stops with `ERROR`.

What a model span records:

- `model.provider` and `model.name`
- `outcome`: `tool_request` or `final_answer`
- `input_tokens`, `output_tokens`, `total_tokens` — **only if the model reported them**

The scripted models report no token usage, because they do not use tokens, so those spans show none. See "Cost and token visibility".

## Instrument tool calls

A proposed tool call becomes a `TOOL_CALL` span with the tool name and sanitized arguments, and two children:

- `TOOL_VALIDATION`: the application's allowlist and argument checks. It ends `OK` (`outcome=allowed`) or `REJECTED` with the reason.
- `TOOL_EXECUTION`: the tool ran. It records a short, redacted `result` summary, or on failure `error.type` and `error.message`.

The `TOOL_CALL` span also carries `proposal=span-003`, a pointer to the `AGENT_DECISION` that proposed it.

## Capture stop reasons

The runtime's stop reasons are the ones from Labs 07 to 09, with the same names: `FINAL_ANSWER`, `MAX_STEPS`, and `REJECTED_TOOL_CALL`. This lab adds one, `ERROR`. The trace records the reason on the root span and in the run summary:

| Runtime stop reason | What happened | Root span status | In the trace |
| --- | --- | --- | --- |
| `FINAL_ANSWER` | The model answered | `OK` | a `FINAL_RESPONSE` span |
| `MAX_STEPS` | The step budget was reached: the safety limit | `ERROR` | `error.type=StepBudgetExhausted`, no final response |
| `REJECTED_TOOL_CALL` | The application refused a proposal | `REJECTED` | a `TOOL_VALIDATION` span in `REJECTED`, no `TOOL_EXECUTION` |
| `ERROR` | A model call, or an allowed tool, failed | `ERROR` | the failed span has `error.type` and `error.message` |

A developer can now tell "the agent produced a final answer" from "the agent stopped because the step budget was reached" from "something broke". See "Implementation decisions" for why the names were kept.

## Add metrics

[`Metrics`](src/main/java/dev/agentic/handbook/labs/observability/Metrics.java) keeps seven in-memory counters across runs:

| Metric | Counts |
| --- | --- |
| `agent.runs` | runs started |
| `agent.failures` | runs that did not end in a final answer |
| `agent.steps` | model decisions, the unit of the step budget (one model call each) |
| `agent.tool.calls` | tool calls the model proposed |
| `agent.tool.rejected` | tool calls the application refused |
| `agent.tool.errors` | allowed tool calls that failed while executing |
| `agent.run.duration_ms` | total wall time of all runs |

Seven, deliberately. Rejected and failed are separate because "the application said no" and "a dependency broke" are different things to watch. There is no registry, no labels, no histogram, and no exporter. At the end of the run the counters look like this:

```
METRICS (in memory, across all runs above)
  agent.runs             3
  agent.failures         2
  agent.steps            9
  agent.tool.calls       8
  agent.tool.rejected    0
  agent.tool.errors      1
  agent.run.duration_ms  25
```

Metrics summarize. They say that two of three runs failed. They cannot say that one of them repeated the same lookup three times. Traces explain.

## Diagnose the failure

Here is the same run from the first section, now observed. First the trace, abbreviated in the middle (the full program prints every span; times are real and will differ on your machine):

```
TRACE run-001
+0ms    span-001  AGENT_RUN       APPLICATION  agent run  [ERROR 19ms]
                                                 error.type=StepBudgetExhausted  error.message=The budget of 4 model decisions was used without a final answer.  stop_reason=MAX_STEPS  steps=4
+5ms    span-002  MODEL_CALL      MODEL          model call 1  [OK 5ms]
                                                   model=scripted/repeating-lookup  outcome=tool_request
+10ms   span-003  AGENT_DECISION  MODEL            propose getServiceStatus(serviceName=notifications)  [OK <1ms]
+10ms   span-004  TOOL_CALL       APPLICATION    getServiceStatus(serviceName=notifications)  [OK 2ms]
                                                   proposal=span-003
+11ms   span-005  TOOL_VALIDATION APPLICATION      allowlist and arguments  [OK <1ms]
                                                     outcome=allowed
+12ms   span-006  TOOL_EXECUTION  TOOL             getServiceStatus  [OK 1ms]
                                                     result=serviceName=notifications, status=DEGRADED, message=Elevated delivery latency and retries since 14:05 UTC.
+16ms   span-007  MODEL_CALL      MODEL          model call 2  [OK <1ms]
                                                   model=scripted/repeating-lookup  outcome=tool_request
...
+18ms   span-017  MODEL_CALL      MODEL          model call 4  [OK <1ms]
                                                   model=scripted/repeating-lookup  outcome=tool_request
+18ms   span-018  AGENT_DECISION  MODEL            propose getRecentDeployment(serviceName=notifications)  [OK <1ms]
+18ms   span-019  TOOL_CALL       APPLICATION    getRecentDeployment(serviceName=notifications)  [OK <1ms]
                                                   proposal=span-018
+18ms   span-020  TOOL_VALIDATION APPLICATION      allowlist and arguments  [OK <1ms]
                                                     outcome=allowed
+18ms   span-021  TOOL_EXECUTION  TOOL             getRecentDeployment  [OK <1ms]
                                                     result=serviceName=notifications, version=notifications-2.4.1, deployedAt=2026-09-23T13:52:00Z, summary=Retry policy adjustment for the email delivery worker.

RUN SUMMARY run-001
  stop reason:  MAX_STEPS -- the step budget was reached before a final answer
  status:       ERROR
  duration:     19ms
  model calls:  4
  tool calls:   4 (0 rejected, 0 failed)
  tokens:       not reported by this model
```

Reading it top to bottom:

1. `model call 1` proposes `getServiceStatus`; the application allows it; it returns `DEGRADED`.
2. `model call 2` proposes `getRecentDeployment`; allowed; it returns deployment `notifications-2.4.1`.
3. `model call 3` proposes `getRecentDeployment` **again**. Allowed. Same result.
4. `model call 4` proposes it a **third** time. Allowed. Same result.
5. The step budget is reached. The run stops with `MAX_STEPS`.

The trace answers what the result could not, and the program reads the trace as data to say so:

```
  MODEL proposed 4 times; APPLICATION allowed 4; TOOL executed 4.
  Every proposal was valid, so the application let each one run: nothing was rejected.

Repeated: getRecentDeployment(serviceName=notifications) was requested 3 times, and returned the same result every time.
Found: the model kept asking the same question and never accepted the answer.
The step budget, an application control, ended the run: stop reason MAX_STEPS.

The diagnosis is also a candidate evaluation case for Lab 09: 'a deployment lookup is
never requested twice with the same arguments'. Observability finds the failure;
evaluation keeps it from coming back.
```

Now the failure mode is clear, and it is not the one "timed out" suggests: nothing was slow, nothing was rejected, no tool failed. The model kept asking the same question and never accepted the answer, and an application control (the step budget) did its job and stopped it. Where to look next is the model's instructions, or how the observation is presented to it, not the tools or the runtime.

> **Break it yourself.** Change `MAX_STEPS` in `ObservabilityExample` to 6 and rerun: the same lookup is now requested five times before the budget stops the run, and the metrics grow while the story in the trace stays the same. Then change `ScriptedModel` so it answers after the first deployment lookup: the run ends in `FINAL_ANSWER`, and the trace shows the difference at once.

The model behind this run is a scripted test double standing in for a plausible failure mode of an agent loop: asking for the same tool again. It is not a claim about how any particular model behaves.

For comparison, a normal run of the same goal (the program prints this too):

```
RUN SUMMARY run-002
  stop reason:  FINAL_ANSWER -- the model produced a final answer
  status:       OK
  duration:     2ms
  model calls:  3
  tool calls:   2 (0 rejected, 0 failed)
  tokens:       not reported by this model
```

## Failure information: an ERROR span

Section 5 of the output makes a tool fail the way a dependency can. The failed span has what is needed to diagnose it, and nothing secret:

```
  2026-10-01T15:58:56.260230559Z ERROR trace=run-003 span=span-044 event=tool_error tool=getRecentDeployment error.type=IllegalStateException error.message="Deployment API returned HTTP 503 for GET /v1/deployments?service=notifications&api_key=[REDACTED]; request header Authorization: [REDACTED]"

+1ms    span-044  TOOL_CALL       APPLICATION    getRecentDeployment(serviceName=notifications)  [ERROR <1ms]
                                                   proposal=span-043
+1ms    span-045  TOOL_VALIDATION APPLICATION      allowlist and arguments  [OK <1ms]
                                                     outcome=allowed
+1ms    span-046  TOOL_EXECUTION  TOOL             getRecentDeployment  [ERROR <1ms]
                                                     error.type=IllegalStateException  error.message=Deployment API returned HTTP 503 for GET /v1/deployments?service=notifications&api_key=[REDACTED]; request header Authorization: [REDACTED]
```

The tool name, the status, the duration, the exception type and the message are all there, and the original exception is not silently dropped: the runtime catches it so the trace can be completed, and records its type and (redacted) message. The simulated error message in the lab's source contains an API key and an `Authorization` header, because error messages from HTTP clients and providers can echo request details. They did not reach the log or the span. See "What should NOT be logged?".

## Evaluation vs observability

| | Evaluation (Lab 09) | Observability (Lab 10) |
| --- | --- | --- |
| Question | Across controlled cases, does the system behave as intended? | What actually happened during this run? |
| Evidence | Chosen cases, deterministic checks, human review | The record of one execution |
| When | Before you rely on a version, and again after every change | While and after a real run executes, including requests nobody anticipated |
| Example | "Case 3 failed because the agent made an unsupported causal claim." | "Trace `run-001` shows model decision, status tool, deployment tool, deployment tool, deployment tool, step limit." |

The two answer different questions about the same scenario, and the program shows both for the same run: three of Lab 09's checks (`ScenarioChecks`) say it failed, and the trace says why. Those three checks are *not* the Lab 09 harness; Lab 09 has the cases, the five dimensions, and the baseline-versus-candidate comparison, and this lab does not rebuild it.

They also feed each other. A trace found the repeated lookup; a good response is a new Lab 09 case — "a deployment lookup is never requested twice with the same arguments" — so the regression cannot come back unnoticed. Evaluation does not replace observability: a case set will never cover every real request. Observability does not replace evaluation: seeing one failure does not tell you how often it happens or whether the fix works.

## What should NOT be logged?

An observable system can accidentally record everything, and then the telemetry itself is a leak. Do not record API keys, passwords, bearer tokens, `Authorization` headers, secrets, raw credentials, or unnecessary sensitive user data. For tool arguments and results, record **safe structured summaries**, redact known secret fields, and do not dump whole payloads.

[`Redactor`](src/main/java/dev/agentic/handbook/labs/observability/Redactor.java) does two small things at one choke point: every value a span or a log line stores goes through it.

- It redacts values that look like credentials: a field named `api_key`, `password`, `secret`, `authorization`, `credential`, `cookie` or `token`; an `Authorization:` header; a `Bearer` token; `password=...`-style assignments; a `?key=` URL parameter; the shape of a Google API key.
- It shortens any value to 200 characters, so a summary is not a payload dump.

It redacts what telemetry *records*, not what the model sees: the tool result still reaches the model unchanged. A test pins that distinction.

**This is not the security milestone.** It is a heuristic over a few known shapes. It will miss a secret in a shape it does not know, and it cannot tell personal data from harmless text. It does not decide who may read a trace, does not defend against hostile content, and does not isolate users. Those are Milestone 11's questions, and they are listed at the end of this lab. The point here is only to make the boundary visible: observability needs a recording policy before it needs a backend.

## Cost and token visibility

An agent may make several model calls per request, so token use per run, not per call, is what matters for cost. This lab records token usage **only when the provider reports it**.

The pinned Google Gen AI Java SDK (`google-genai` 1.72.0) exposes `usageMetadata()` on a `GenerateContentResponse`, an optional with, among others, `promptTokenCount`, `candidatesTokenCount`, `thoughtsTokenCount`, and `totalTokenCount`, each an optional integer. `GeminiAgentModel` maps three of them to `input_tokens`, `output_tokens`, and `total_tokens`, field by field, and leaves any absent field absent. The SDK exposes these counts separately and optionally (it also has `thoughtsTokenCount`, `toolUsePromptTokenCount`, and `cachedContentTokenCount`), so input plus output need not equal the total. The lab records what is reported and does not reconcile it, and this README does not define how the provider computes the total. The scripted models report nothing, so the default run shows `tokens: not reported by this model`.

The adapter's mapping is tested against responses built in the test, and this repository does not run it against the live API, so the live token fields are unverified here beyond the SDK's types and the documentation. There is no price table and no billing integration: converting tokens to money is a production concern this lab does not take on.

## Duration and latency

Every duration is the difference between two reads of an injected clock (`java.time.InstantSource`). The default run uses the system clock, so durations are real measurements: for the scripted models they are tiny (the output shows `<1ms`), and most of them are real time spent in local Java code. The tests inject a fake clock that advances a fixed step per read, so no test depends on real time. The clock is wall-clock time, which is simple but not monotonic; a production system would prefer a monotonic source for durations. Nothing in the lab sleeps to make a duration look interesting.

## Decision authority and observability

[LLM vs Decision Authority](../../docs/model-vs-decision-authority.md) says the model may propose and the application owns whether a proposal becomes executable. The trace makes that visible, with an `Actor` label on each span type:

| Span | Actor | Says |
| --- | --- | --- |
| `AGENT_DECISION` | MODEL | "I propose `getRecentDeployment(notifications)`." |
| `TOOL_VALIDATION` | APPLICATION | "That is on the allowlist and the arguments are valid" — or `REJECTED` |
| `TOOL_EXECUTION` | TOOL | "I ran it, and this is the result." |

In the repeating run the program prints the consequence: the model proposed four times, the application allowed four, the tool executed four. Nothing was rejected, because every proposal was valid, and the step budget, an application control, stopped the run.

Two cautions. The `Actor` is a label that records who acted; it grants nothing. And the observability plane **observes** the system; it must not become a decision authority. In this lab nothing the runtime branches on comes from `Telemetry`: remove all recording and the runtime makes identical decisions. (The one exception to "recording only" is the runtime splitting validation from execution so the boundary has two spans; rejections behave exactly as in Lab 09.)

## MCP continuity

Lab 08 moved a tool behind an MCP server. The same instrumentation applies: the `TOOL_EXECUTION` span would wrap the client's call to the server, and the span would record the same name, a redacted result summary, a duration, and a failure if the call failed. This lab does not demonstrate it, because the MCP server is a separate process and showing the whole path (agent, MCP tool call, MCP server, tool result) as one trace needs the trace id to cross the process boundary. That is **context propagation**, and this lab does not implement it: with MCP, the server's own work would appear as a gap, or as a separate trace. OpenTelemetry's GenAI conventions repository has a page for MCP; this lab does not use it and makes no claim about it beyond that. Local tools keep the lab about observability, not about process startup and protocol plumbing.

## Optional production tooling: OpenTelemetry

This lab uses no OpenTelemetry dependency, deliberately. The concepts (trace, span, parent, correlation id, attributes, status) are clearer in a few hundred lines of plain Java you can read than behind a provider, a builder, and an exporter configuration, and the lesson is why you record these things, not how to configure a library.

What OpenTelemetry is, from its documentation:

- An open standard and a set of components: a specification (an API, an SDK requirements document, and a data protocol, OTLP), language-specific API and SDK implementations, instrumentation libraries, exporters, and the Collector, a vendor-neutral proxy that can receive, process, and export telemetry. It produces and ships telemetry; storing and querying it is the job of a backend you choose.
- Its span model is the one this lab teaches in miniature: a name, a parent span id (empty for a root), start and end timestamps, a span context (trace id and span id), attributes, events, links, and a status. The documentation's examples show 32-hex-character trace ids and 16-hex-character span ids.
- **Context propagation** is what makes tracing work across processes: the context (trace id and span id) travels with a call, so the receiver can create spans that belong to the same trace. By default it uses the W3C Trace Context `traceparent` header. It is what an MCP server, or any remote tool, would need for its work to join the agent's trace. OpenTelemetry SDKs can also inject trace and span ids into log records, which is the correlation you saw in the log lines above, done by a library.
- Its status values are `Unset`, `Error`, and `Ok`, not this lab's `OK`, `REJECTED`, `ERROR`. This lab's three are a teaching choice.
- The GenAI semantic conventions (standard attribute names for GenAI telemetry) have moved to the [OpenTelemetry GenAI semantic conventions repository](https://github.com/open-telemetry/semantic-conventions-genai), as the OpenTelemetry documentation page for them now states. Their stability and contents change, so that repository is the place to check. Attribute names in this lab (`model.name`, `error.type`) are not claimed to match them.

You might choose it when you run more than one service, need traces to cross process boundaries, want one standard instead of a vendor's SDK, or must send telemetry to a backend someone else operates. You might not when you have a single process and a log you can read: a trace tree printed to a terminal, or a few structured lines, may be all you need. **Not every application needs a tracing backend.**

This handbook stays vendor-neutral. Production tooling exists for storing, searching, and alerting on traces; none of it is needed to understand the architecture, and none of it is a dependency here.

## Run it

Deterministic, no key, no network, no telemetry backend, no Docker:

```sh
./mvnw -pl labs/10-observability compile exec:java
```

Windows: `.\mvnw.cmd -pl labs/10-observability compile exec:java`

The output has six sections:

1. the failed run, seen from the outside (a plain log line and the Lab 09 style checks)
2. the same run, observed: structured log lines, the trace tree, the run summary
3. the diagnosis, read from the trace
4. a normal run, for comparison
5. a failing tool: an `ERROR` span, and redaction
6. the metrics across the three runs

Timestamps and durations in the output come from the real clock and differ on every run; the structure, the ids (`run-001` to `run-003`), and the stop reasons do not.

## Optional: run it with Gemini

```sh
./mvnw -pl labs/10-observability compile exec:java -Dexec.args="--live"
```

Requires `GOOGLE_API_KEY` (see [.env.example](.env.example)); `GEMINI_MODEL` overrides the default (`gemini-3.8-flash`). The live run executes the notifications goal once with the **same** runtime and produces the **same** kind of trace, log lines, summary, and metrics; only the model's behavior differs, and token counts appear if the provider reports them. Neither the build nor CI needs the key or calls Gemini, and this lab does not run it for you. A live run is one run of a probabilistic system, not a benchmark, and no live output is recorded in this repository.

## Do I actually need this?

- **A script you run once?** A few log lines may be enough.
- **A model-backed feature that real users depend on?** Decide what you will need to reconstruct a failed run *before* it fails: a correlation id, model and tool calls with their arguments and results (summarized), durations, and the stop reason.
- **More than one service, or a regulated environment?** Then a standard such as OpenTelemetry, a collector, and a backend start to earn their cost.
- **Never record more than you can justify.** More telemetry is not better telemetry.

Do not build a telemetry SDK for the whole repository, and do not build one for your product either if a mature library already fits: this lab's classes are a teaching model, not something to depend on.

## Tests

`./mvnw -pl labs/10-observability verify` runs the tests below, all deterministic and offline. Time comes from a fake clock; ids are only compared with each other.

| Point | Test |
| --- | --- |
| Every run gets a trace id | `TraceTest.everyRunHasATraceIdThatEverySpanShares` |
| Parent/child relationships | `spansNestUnderTheirParents` |
| Model spans | `everyModelDecisionIsAModelSpanWithItsProposal` |
| Tool calls | `everyToolCallRecordsProposalValidationAndExecution` |
| Results on the right span | `eachResultIsAttachedToItsOwnToolCall` |
| Failures | `aToolFailureIsAnErrorSpanWithTypeAndMessage`, `aModelFailureIsAnErrorOnTheModelSpanAndStopsTheRun`, `aRejectedProposalIsRejectedNotAnError` |
| Stop reasons | `stopReasonsAreRecordedOnTheRootSpan` |
| Durations | `durationsAreNonNegativeAndConsistent`, `theRealClockNeverProducesANegativeDuration` |
| Metrics | `metricsCountWhatTheTracesContain` |
| Distinct trace ids | `multipleRunsDoNotShareTraceOrSpanIds` |
| Redaction | `RedactionTest` |
| The failing scenario | `theRepeatingRunTraceExposesTheRepeatedLookupAndTheBudgetStop` |
| Default run needs no network | `ObservabilityExampleTest.theDefaultRunNeedsNoKeyAndShowsTheTraceTheLogsAndTheMetrics` (empty environment, no error) |

Token usage is tested both ways (absent unless reported), and the Lab 09 runtime contract (budget, allowlist, rejection) is re-tested with the instrumentation in place.

## Reflection

1. The run failed with `MAX_STEPS`. Name three different causes that would produce the same result, and say which spans would tell them apart.
2. Why is the tool result an attribute of `TOOL_EXECUTION` rather than a separate unrelated log line?
3. A rejected proposal and a tool error both stop the run. Why do they have different statuses?
4. `agent.failures = 2` and `agent.runs = 3`. What can you conclude, and what can you not?
5. Where in the trace is the decision-authority boundary? What would it look like if the model could skip validation?
6. The redactor catches `api_key=...`. Write a secret it would miss.
7. Two runs interleave on one machine. Which fields keep their log lines apart?
8. What would you need to add before this trace could cross into an MCP server's process?
9. You want a trace tree for a throwaway script. What is the smallest thing worth keeping?

Short guidance: the model repeating itself, a slow tool that returns nothing new, and a budget that is simply too small — the tool-call spans, their results, and the durations tell them apart (1); because a result belongs to one tool call, and a log line has no such relationship (2); a rejection means the application's controls worked, an error means something broke, and you alert on them differently (3); that two runs did not end in an answer, but not why, not which, and not how often each cause occurs (4); between `AGENT_DECISION` and `TOOL_EXECUTION`, in `TOOL_VALIDATION`; without it there is no application decision to see (5); a secret in a shape the patterns do not know, such as a bare token with no label, or a password inside prose (6); the trace id and the span id (7); trace context propagation, and a server that records spans under the id it receives (8); the trace id and one line per event with a stop reason (9).

## Implementation decisions

- **Stop reasons keep the runtime's names.** Labs 07 to 09 call the three reasons `FINAL_ANSWER`, `MAX_STEPS`, and `REJECTED_TOOL_CALL`, and Lab 09's evaluation checks depend on them. Renaming them to `FINAL_RESPONSE` and `STEP_LIMIT` would silently change earlier concepts. The trace distinguishes them by the `stop_reason` attribute, the root span's status, and the span types (`FINAL_RESPONSE` exists only for an answer). `ERROR` is the only new reason: earlier labs let a model or tool exception escape the runtime, which would leave a trace with unfinished spans, so here the run stops and records it. Mapping to the vocabulary you may meet elsewhere: final response is `FINAL_ANSWER`, step limit is `MAX_STEPS`.
- **Decision authority as span types, not a framework.** `AGENT_DECISION`, `TOOL_VALIDATION`, and `TOOL_EXECUTION`, each with an `Actor`. Validation and execution were one method in Lab 09 and are two steps here; the observable behavior is the same. See "Decision authority and observability".
- **Spans, not events.** Everything is a span with attributes, so there is one concept instead of two. A tool result is an attribute of its execution span; a model decision is a short child span of its model call, a point-in-time marker that starts and ends right after the call returns.
- **Redaction at one choke point.** The recorder and the log redact every value they store, so a caller cannot forget. It is a heuristic, and the README says so.
- **Token usage only where it exists.** `AgentModel` gained two default methods (`info()` and `lastUsage()`), purely to *report*; they never influence a decision.
- **An injectable clock** (`InstantSource`), so no test reads real time. Durations are derived, never stored separately.
- **No OpenTelemetry, and no new dependency.** The only production dependency is the Google Gen AI SDK, carried over from Labs 01 to 09 at the same version (1.72.0) for the optional live run only. JUnit is the only test dependency. See "Optional production tooling" for the reasoning.
- **Local tools, not MCP.** See "MCP continuity".
- **Sequential ids and in-memory everything.** Nothing is exported or stored: a trace is printed, then gone. There is no sampling, no persistence, and no thread safety beyond atomic id counters; the runtime is single-threaded.
- **Few log lines.** One per completed tool call, one per failure, one at the end of the run. A log line is not a substitute for the trace.
- **Three borrowed checks, not a harness.** `ScenarioChecks` shows evaluation and observability on the same run. Lab 09 is the evaluation harness.
- **No ADR.** The lab-level choices here (a small in-memory trace model, no OpenTelemetry) are not a rule later work must follow, so they are recorded in this README. If a later milestone makes "every agent execution must carry a trace id across model and tool boundaries" a project-wide requirement, that would deserve an ADR then; this lab does not make it one.

## What we STILL do not have

- No tracing backend, no storage, and no way to query traces: a trace is printed once
- No OpenTelemetry, no standard export format, and no collector
- No context propagation across processes, so no trace that continues into an MCP server
- No sampling, no retention policy, and no alerting, dashboards, or service-level objectives
- No latency distribution: metrics are plain counters and one sum
- No cost calculation, and no live token numbers recorded in this repository
- No recording policy beyond a heuristic redactor, and no access control on traces
- No multi-agent or A2A tracing, and no concurrent runs
- No evaluation built from traces, and no online evaluation of a running system
- No deployment: the lab is a JVM you start by hand

## What limitation remains?

We can now reconstruct what happened in a run. But observing a system means **collecting execution data**, and that data is itself sensitive. Once we start recording tool arguments, tool results, error messages, and model decisions, observability becomes a security and privacy concern. The redactor in this lab covers a few shapes; it is not an answer.

Questions this lab does not solve:

- What data may be logged, and what may never be?
- How should secrets be redacted, and how do we know it worked?
- Who may read traces?
- Can prompt injection influence telemetry, for example by writing misleading text into a tool result that then lands in a log?
- Can tool results leak sensitive data into a log or a trace?
- How should tenant and user boundaries work in recorded data?

That is **Milestone 11 — Security**, taught in [Lab 11](../11-security/README.md). This lab does not solve it, and its redactor is still only the heuristic described above.

## Sources consulted

Verified on 2026-10-01. The trace model, the span types, the stop-reason handling, the metrics, and the redactor are this handbook's own teaching design; the OpenTelemetry material below supports the general claims made about observability concepts and is not the source of the lab's design. No vendor's product is used, endorsed, or cited.

- OpenTelemetry, official documentation (opentelemetry.io):
  - [Observability primer](https://opentelemetry.io/docs/concepts/observability-primer/): definitions of observability, telemetry, instrumentation, logs, spans, and traces; the statement that logs lack context and are more useful when correlated with a trace and span.
  - [Traces](https://opentelemetry.io/docs/concepts/signals/traces/): spans and their fields (name, parent span id, start and end timestamps, span context, attributes, events, links, status), span status values `Unset`, `Error`, `Ok`, the example trace and span id formats, and that spans resemble structured logs with correlation and hierarchy.
  - [Context propagation](https://opentelemetry.io/docs/concepts/context-propagation/): context carrying trace id and span id between services, the default W3C Trace Context `traceparent` header, and log correlation by injecting trace and span ids.
  - [Metrics](https://opentelemetry.io/docs/concepts/signals/metrics/) and [Logs](https://opentelemetry.io/docs/concepts/signals/logs/): the definitions of a metric and a log used above.
  - [Components](https://opentelemetry.io/docs/concepts/components/): specification (API, SDK, data), the Collector, language SDKs, instrumentation libraries, and exporters.
  - [Generative AI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/), which states that GenAI semantic conventions have moved to the [OpenTelemetry GenAI semantic conventions repository](https://github.com/open-telemetry/semantic-conventions-genai). Not used by this lab.
- Provider-specific, for the optional live mode only:
  - [Gemini API reference, `generateContent`, `UsageMetadata`](https://ai.google.dev/api/generate-content): the usage fields.
  - The `google-genai` 1.72.0 SDK itself: `GenerateContentResponse.usageMetadata()` and the optional `promptTokenCount`, `candidatesTokenCount`, `thoughtsTokenCount`, and `totalTokenCount` fields, read from the 1.72.0 jar's public types.
  - [Function calling with the Gemini API](https://ai.google.dev/gemini-api/docs/function-calling), as in Labs 07 to 09. This runtime supports at most one function call per decision and stops with an error otherwise.
- Not cited on purpose: vendor observability and tracing products. Their feature lists are not evidence for how this lab works, and none is a dependency.
