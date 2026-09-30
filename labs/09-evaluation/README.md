# Lab 09 — Evaluation

**A test asks whether a component obeys its contract. An evaluation asks whether the system behaves the way we want across representative cases.**

**One successful demo is not evidence of reliable behavior.**

**Evaluate the trajectory, not only the final answer.**

## The limitation we found

Lab 08 ended with an agent that works: a bounded runtime, a model that proposes the next step, and two read-only tools — one a local method, one behind an MCP server. Every piece was shown to work, one deterministic path at a time. The build is green, the demos are convincing, and the tests pass.

None of that answers the question a team has to answer before it relies on the system:

> How do we know this agent still does what we intended when the inputs and the model's behavior vary?

Unit and integration tests check that deterministic components obey their contracts: the runtime stops at its budget, the allowlist rejects an unknown tool, the MCP client fails on a changed contract. Those tests are valuable, and nothing in this lab replaces them. But a model-backed system can pass every one of them and still get *worse* — because the thing that changed is not a contract the tests state. This lab makes that visible, then builds the smallest thing that catches it.

## Learning goals

- Tell a **test** from an **evaluation**, and a **demo** from evidence
- Evaluate both the **final answer** and the **trajectory** that produced it
- Write expectations as **behavioral properties**, not as one correct string
- Build a small, readable, versioned **evaluation set** — with more than happy paths
- Build the smallest useful **harness**: run, capture a trace, check, report
- Keep **individual failures visible** instead of hiding them in one number
- Compare **baseline and candidate** on the same cases and see a regression
- Know what deterministic checks can and cannot judge, and where a **model judge** or a **human** is needed
- Decide when a system needs an evaluation set at all

## Starting point

The same fictional Helio platform and the same bounded agent from Lab 07: a runtime with a budget of 4 model decisions, and two read-only tools, `getServiceStatus` and `getRecentDeployment`. The goal is the one you already know:

> "Investigate the notifications service. If it is degraded, check whether there was a recent deployment and summarize what is known. Do not claim a root cause without evidence."

This lab does not send the tools through MCP. The tools are local Java methods, on purpose. Evaluation sits around *system behavior*; it makes no difference whether a capability is a local method, a REST call, gRPC, or an MCP server. Making every evaluation run start a second process would make the lab about process startup and protocol plumbing instead of about evaluation. Lab 08 is unchanged, and MCP has not left the architecture — this lab just does not need it. You probably don't need all of these applies to labs, too.

The lab carries its own copy of the Lab 07 runtime rather than importing it, so it reads on its own. That duplication is deliberate.

## Experience: the demo works

Run the Lab 07 agent on the notifications goal and read the answer:

> notifications is DEGRADED. Elevated delivery latency and retries since 14:05 UTC. The most recent deployment is notifications-2.4.1 (...), deployed at 2026-09-23T13:52:00Z. The timing may be relevant, but the observations show correlation only: whether the deployment caused the degradation cannot be concluded from this evidence, and the root cause is not established.

It checked the status, it looked at the deployment only because the service was degraded, it reported what it saw, and it declined to blame anything. Good.

It is also **one case, one run, one reader who wanted it to be good**.

## Naive approach: "looks good to me"

The reflex is to keep going: change the prompt, run the demo again, read the answer, nod. That is manual review, and for a throwaway experiment it is a perfectly fine amount of rigor. It stops working as soon as the behavior matters, for three reasons:

- **You only look at what you ran.** The prompt you improved for notifications may have quietly changed what happens for a healthy service, and you did not run that.
- **You read what you expect.** A confident sentence that turns correlation into cause is easy to skim past when the rest of the answer looks right.
- **Nothing is left behind.** Next month, when someone changes the prompt, the model, or a tool, there is no record of what "good" was.

## Break it: introduce a behavioral regression

Now change the system the way real systems change — a prompt tweak, a model upgrade. The scripted `candidate` in this lab stands in for that change, with two deliberate regressions:

1. It **always** looks up the deployment, even when the service is healthy or under planned maintenance.
2. When a deployment exists, its answer says the deployment **caused** the incident.

Run the demo case again and look at the candidate's answer:

> notifications is DEGRADED. Elevated delivery latency and retries since 14:05 UTC. The most recent deployment is notifications-2.4.1 (...), deployed at 2026-09-23T13:52:00Z. The deployment notifications-2.4.1 caused the incident.

The observations are identical. The run still ends on a final answer, in three decisions, inside the budget. Every test of the runtime, the allowlist, and the tools still passes — none of them says anything about what the answer may claim. And it would be easy to miss: the answer is fluent, it uses the right facts, and it is wrong in exactly the way the goal forbade.

**Tests can all pass while behavior gets worse.** That is the gap an evaluation fills.

## Build an evaluation set

An evaluation is only as good as its cases. [`HelioIncidentsV1.java`](src/main/java/dev/agentic/handbook/labs/evaluation/HelioIncidentsV1.java) holds six, small enough to read in one sitting:

| Case | Kind | Why it exists |
| --- | --- | --- |
| `degraded-after-deployment` | Normal | The representative case: degraded service, deployment shortly before. Correlation, not cause. |
| `healthy-service` | Negative | Nothing is wrong. The right behavior is to do *less*: no deployment lookup, no invented incident. |
| `degraded-no-deployment` | Boundary | Degraded, so the lookup is justified — and finds nothing. Do not invent a deployment, do not fill the gap with a cause. |
| `planned-maintenance` | Boundary | `MAINTENANCE` is neither healthy nor degraded; reviewers can disagree. The set decides and says so. |
| `leading-question` | Regression guard | "Was it the deployment?" Same evidence as the normal case, more pressure to blame. Added *after* the regression above, so it cannot return silently. |
| `unknown-service` | Failure | No such service. The defined safe behavior is a rejected request and a stop: no manufactured status, no answer. |

Two facts about the scenario data are worth knowing. `search` keeps its Lab 07 and 08 status, planned maintenance, so it became the boundary case instead of an invented degradation. And `checkout` is new to this lab: a degraded service with no recent deployment, so the set can include a lookup that comes back empty.

**Representation.** The set is a plain Java collection of records, not JSON or JSONL. It is version-controlled, diffable in review, needs no parser and no dependency, and cannot drift from the types that check it. A resource format would add a parsing step without teaching anything — if a future lab needs cases from outside Java, that is the moment to add one. The set has a name, `helio-incidents-v1`; a report is only comparable with another run of the *same* set, so change the name when the meaning changes.

Each case carries an `id`, an input (`goal`), the scenario (fixed data in [`ServiceTools`](src/main/java/dev/agentic/handbook/labs/evaluation/ServiceTools.java)), a `why`, and its expectations. The `why` is not decoration: it is what lets a reviewer disagree with a case and change it, instead of quietly loosening a check.

## Define behavioral checks

A case's expectations are **properties**, never a single correct answer. There is no universally right natural-language answer to "investigate this service", and a check that demands one will fail on every harmless rewording. [`EvalCase.Expectations`](src/main/java/dev/agentic/handbook/labs/evaluation/EvalCase.java) says:

- which tool requests are **required** (any order)
- which tools are **forbidden**, because the case does not justify them
- the **intended stop reason** and the **step budget**
- phrases the answer **must** and **must not** contain

**Behavior, not implementation.** The good expectation is "the agent must not claim a root cause when the evidence only shows correlation". The bad one is "the answer must equal this 183-character string", or "tool call number 2 must be exactly this, because the current code happens to do it". A rewritten prompt, a renamed class, or one extra harmless decision must not fail an evaluation.

There is exactly one check that is **exact on purpose**: `getServiceStatus` must be the first request. That is architecture, not implementation — the goal is conditional on the status, so no case justifies asking about a deployment before knowing whether anything is wrong. Everything else is flexible. When you write a check, ask which kind it is, and be honest about it.

The lab evaluates five dimensions. It is a small set on purpose: each is a different *kind* of check, and together they cover the trajectory and the answer.

| Dimension | Looks at | Example check |
| --- | --- | --- |
| Tool selection | trajectory | `requested getRecentDeployment(notifications)`; status checked first |
| Tool restraint | trajectory | `did not request [getRecentDeployment]` when the case does not justify it |
| Bounded execution | runtime behavior | stayed within the step budget; stopped for the intended reason |
| Final outcome | answer | reports the observed status; mentions the deployment that exists |
| Evidence discipline | answer against observations | no unsupported root-cause claim; cites only version identifiers that were observed |

## Evaluate the trajectory

For an agent, the final answer is the smallest part of the behavior. Two runs can end with the same sentence after very different work — one that inspected the deployment because the service was degraded, and one that inspected everything because it always does. The answer cannot tell them apart. The **trajectory** can: which tools were requested, with which arguments, in what order, after how many model decisions, why the run stopped, and what observations were available to the model.

[`AgentRunResult`](src/main/java/dev/agentic/handbook/labs/evaluation/AgentRunResult.java) already carries all of that — the same plain record as Labs 07 and 08 — and adds `requests()`: every tool the model *asked for*, including a rejected one that ended the run. A trajectory is what was proposed, not only what was executed; the unknown-service case depends on it.

**An evaluation trace is not an observability trace.** This one is captured for a controlled run and judged against expected behavior. Observability traces are runtime telemetry from a production system. They answer a different question, and they are Milestone 10. Nothing here is OpenTelemetry, and nothing here is production logging.

## Evaluate the final answer

Natural language is where deterministic checks are weakest, so this lab uses only properties that can honestly be verified deterministically:

- the answer contains the observed service status
- the answer mentions the deployment identifier when one exists
- every version identifier in the answer appears in an observation (a fact from nowhere is a failure)
- the answer does not contain phrases the case forbids
- the answer contains no **unhedged causal sentence**

The last one deserves honesty. [`BehaviorChecks.unsupportedCausalClaims`](src/main/java/dev/agentic/handbook/labs/evaluation/BehaviorChecks.java) splits the answer into sentences and flags one that asserts a cause — *caused*, *broke*, *due to*, *root cause is* — with no hedge or negation. It flags "The deployment caused the incident." It accepts "Whether the deployment caused it cannot be concluded." It is **intentionally simplistic**:

- a **paraphrase** ("this is on the deployment") slips through
- a **hedge in the same sentence** hides a claim ("it caused the incident, although I could be wrong")

The tests pin both limits down in the open. A substring or a pattern is not semantic evaluation, and this lab does not pretend otherwise. What it cannot judge — clarity, completeness, tone, whether an explanation is actually *right* — needs a person, or a model judge used carefully (both below).

## Run baseline vs candidate

[`EvaluationRunner`](src/main/java/dev/agentic/handbook/labs/evaluation/EvaluationRunner.java) is the whole harness in one method: for every case, run the system under evaluation with the real bounded runtime (with a fresh model per case), capture the trace, and apply the checks. No framework, no plugin points, no persistence.

The system under evaluation is a `SystemVersion` — a name and a way to get a model. The runtime, the cases, and the checks are the same for every version, which is exactly what makes two versions comparable:

> same cases + same checks + different system version = a reviewable behavioral change

## Run it

Deterministic, no key, no network, no model call:

```sh
./mvnw -pl labs/09-evaluation compile exec:java
```

Windows: `.\mvnw.cmd -pl labs/09-evaluation compile exec:java`

The scripted behaviors are test doubles, not AIs — small rule-based "models" whose decisions depend on what they observe. **They make the evaluation harness deterministic and inspectable. Live-model evaluation is the probabilistic use case of the same harness.** A scripted run demonstrates the mechanics; it is not a real model evaluation.

## Read the evaluation report

The baseline, one case (the full run prints every case and every check):

```
[PASS] degraded-after-deployment  (NORMAL)
  PASS  status checked first (exact order)
  PASS  requested getServiceStatus(notifications)
  PASS  requested getRecentDeployment(notifications)
  PASS  stayed within the step budget
  PASS  stopped for the intended reason (FINAL_ANSWER)
  PASS  a final answer was produced
  PASS  answer reports the observed status
  PASS  answer mentions 'DEGRADED'
  PASS  answer mentions 'notifications-2.4.1'
  PASS  no unsupported root-cause claim
  PASS  answer cites only observed version identifiers
  Result: 11 / 11 checks passed
```

The same case for the candidate:

```
[FAIL] degraded-after-deployment  (NORMAL)
  ...
  FAIL  no unsupported root-cause claim
        unhedged causal claim: "The deployment notifications-2.4.1 caused the incident."
  PASS  answer cites only observed version identifiers
  Result: 10 / 11 checks passed
```

Then the two versions side by side, and every individual regression:

```
                           baseline     candidate
Cases acceptable           6 / 6        2 / 6
Checks passed              63 / 63      59 / 63
Tool selection             15 / 15      15 / 15
Tool restraint             4 / 4        2 / 4
Bounded execution          12 / 12      12 / 12
Final outcome              18 / 18      18 / 18
Evidence discipline        14 / 14      12 / 14

Passed for the baseline, fails for the candidate:
  degraded-after-deployment -> [Evidence discipline] no unsupported root-cause claim
  healthy-service -> [Tool restraint] did not request [getRecentDeployment]
  planned-maintenance -> [Tool restraint] did not request [getRecentDeployment]
  leading-question -> [Evidence discipline] no unsupported root-cause claim

REGRESSIONS FOUND: 4 check(s) in 4 case(s)
```

Read it the way a reviewer would. Bounded execution and tool selection are unchanged, which is why every runtime test still passes; the two regressions are in tool restraint and evidence discipline, two *different* dimensions, and they show up in two different kinds of case. `degraded-no-deployment` and `unknown-service` are unaffected, and they should be.

**There is deliberately no "Agent Score".** Summing to one number would erase what matters: which case, which check, and how much it matters. "59 / 63" hides a fabricated root-cause claim about a production incident inside 58 boring passes, and not all failures have equal product impact — no weighting can say which do. The counts are a summary to navigate by, always traceable back to the case and check underneath.

## Tests vs evaluations

| | Test | Evaluation |
| --- | --- | --- |
| Question | Does this deterministic component obey its contract? | Across representative cases, does the system exhibit the behavior we want? |
| Subject | A class, a boundary, a protocol | Whole-system behavior: the answer *and* the trajectory |
| Verdict | Pass or fail, every time | Per-case and per-check results; a threshold is a product decision |
| Inputs | The ones the author thought of | A set chosen to be representative, including awkward cases |
| Fails when | A contract is broken | Behavior we wanted drifts, even though nothing "broke" |
| Runs | On every build | On every change to a prompt, model, tool, or runtime behavior |

Both are needed. This lab has tests of its own — including tests that prove each check can fail, on hand-built traces, because a check that has never been seen to fail proves nothing. Those tests protect the *evaluation machinery*. The evaluation protects the *behavior of the system*.

Other pairs that get confused:

- **Demo ≠ evaluation.** A demo shows that something can work. An evaluation shows how often, and where, it does not.
- **Evaluation dataset ≠ production traffic.** The set is hand-picked and fixed; production is whatever users send. A good set is informed by production, and never a substitute for watching it.
- **Final-answer quality ≠ whole-agent quality.** An answer can be right for the wrong reasons, or wrong after a flawless trajectory.
- **Observability ≠ evaluation.** Observability shows what happened on a real request. Evaluation gives controlled evidence about quality on cases you chose. Milestone 10.
- **Evaluation ≠ monitoring.** Monitoring watches a running system over time. Evaluation checks a version, before you rely on it.
- **Evaluation ≠ security testing.** Whether a hostile input can subvert the system is a different question with different cases and a different mindset. Milestone 11.

## Deterministic checks vs model judges

Some qualities cannot be stated as a rule: clarity, completeness, tone, nuanced grounding, whether a summary is actually correct. A **model judge** — a second model prompted to grade an output — can help with those. This lab does **not** implement one, and canonical evaluations should prefer deterministic checks wherever the behavior can be stated deterministically.

A judge is not ground truth. It is another probabilistic system, and it brings its own problems:

- **Variance.** The same output can get different grades on different runs.
- **Prompt sensitivity.** Rewording the grading instructions moves the scores.
- **Biases.** Published work on model judges reports position bias, verbosity bias (favoring longer answers), and self-enhancement bias (favoring output that resembles the judge's own), along with limited reasoning ability on some tasks.
- **Calibration.** Its grades are only meaningful once compared with human judgments on the same cases; without that you do not know what a "4 out of 5" means.
- **Cost and latency.** Every judged case is another model call.

If you add one: label it probabilistic, make it opt-in, keep it out of CI, calibrate it against human review, and treat it as evidence for a reviewer, not as the verdict. Most of this lab's checks need none of that, and that is the point.

## Human review

Some behavior still needs a person: whether an answer is what an on-call engineer would want, whether a boundary case is decided the right way, whether "acceptable" is acceptable. The report includes a small reviewable format — `ReportPrinter.printReview` — that shows what a person needs to decide:

```
CASE:   degraded-after-deployment  (NORMAL)
Why:    The Lab 07 goal: the representative case. ...
Goal:   Investigate the notifications service. ...
Trajectory:
  1. getServiceStatus {serviceName=notifications}
     observed: {serviceName=notifications, status=DEGRADED, ...}
  2. getRecentDeployment {serviceName=notifications}
     observed: {serviceName=notifications, version=notifications-2.4.1, ...}
Stop:   FINAL_ANSWER after 3 model decision(s), budget 4
Answer: notifications is DEGRADED. ... The deployment notifications-2.4.1 caused the incident.
Checks: 10 / 11 passed -- failing: [no unsupported root-cause claim]
Would you accept this behavior? The checks inform that decision; they do not make it.
```

It is terminal text and it pastes into a Markdown review comment. There is no UI and no annotation platform, and there does not need to be. For high-impact decisions, deterministic checks alone are unlikely to be enough: involve the people who own the domain.

## Evaluation dataset quality

The cases are the evaluation. A small set is fine; a *narrow* one is a trap.

- **More than happy paths.** The set has a normal case, a negative case (do less), two boundary cases (reasonable people could disagree), a failure case (stop safely), and a regression guard. If every case resembled the prompt you tuned against, you would be measuring memorization of your own examples, not robustness.
- **Regressions become cases.** `leading-question` exists because the candidate above showed a way to fail. Once a behavior has regressed, it belongs in the set permanently.
- **A boundary case must say why.** `planned-maintenance` expects no deployment lookup. That is a judgment call, written in the case's `why`, where it can be argued with.
- **Small is a feature.** Six cases can be read, argued about, and kept correct. Hundreds of artificial cases cannot, and they do not make the set more representative.

**Development examples vs evaluation cases.** Do not repeatedly tune a prompt against your whole evaluation set until everything passes and then call the result independent evidence. You will have taught the system your answers. Development examples help you build; evaluation cases help you check; keep some cases independent of the ones you iterated on. This lab needs no data-splitting machinery to make the point — it is a discipline, not a feature.

## Decision authority

Connect this to [LLM vs Decision Authority](../../docs/model-vs-decision-authority.md). **Evaluation does not transfer authority to the model.** It checks whether the influence the application delegated is behaving acceptably *inside* the application's decision envelope.

The model may propose "check the recent deployment". The application still decides whether that tool is allowed, whether the arguments are valid, and whether the action executes — `AgentRuntime` does that in deterministic code, and its tests prove it. The evaluation asks a different question about the same delegation:

> Across our cases, does this delegation behave as intended?

The two are complementary. The runtime makes sure a bad proposal cannot *execute*. The evaluation tells you how often the model makes proposals you would not want — a healthy service that gets a deployment lookup is still an allowed, valid, harmless call, and it is still a regression.

## Optional: run it with Gemini

```sh
./mvnw -pl labs/09-evaluation compile exec:java -Dexec.args="--live"
```

Requires `GOOGLE_API_KEY` (see [.env.example](.env.example)); `GEMINI_MODEL` overrides the default (`gemini-3.8-flash`). The live run uses the **same** evaluation cases, the **same** bounded runtime, and the **same** checks, and prints the same per-case results and summary, plus the failing cases in the review format. Neither the build nor CI needs the key or calls Gemini, and this lab does not run it for you.

Read a live report for what it is. It is **one run of a probabilistic system**, not a benchmark, and no live results are recorded in this repository as facts. Pass rates vary between runs and between models: in a real system you repeat the set, read the variance, and treat a case that passes two times in three as a finding, not as a pass. A check that fails for the live model is a claim about that run; a case the live model gets *right* is not proof that it always will.

## Do I actually need an evaluation set?

This is not the same question as "do I need MCP?". Evaluation is not another runtime capability you add to an architecture; it is how you find out whether the architecture you chose is doing its job. The useful question is not "should I install an evaluation framework?" — none is needed, and this lab uses none.

- **One throwaway experiment?** Manual review may be enough.
- **A model-backed feature whose behavior matters?** Define representative cases *before* relying on it.
- **Changing a prompt, a model, a tool, or runtime behavior?** Rerun the same set to see what moved.
- **High-impact decisions?** Deterministic checks alone are unlikely to be enough. Involve domain experts and human review.

Not every hobby prompt needs an evaluation platform. But if behavior matters, evidence should replace demo-driven confidence.

## Reflection

1. Every runtime test still passes for the candidate. Which kind of failure does that show tests cannot catch?
2. Why is "`getServiceStatus` is the first request" an exact check while "`getRecentDeployment` was requested" is not?
3. Two runs end with the same answer. What can the trajectory tell you that the answer cannot?
4. `planned-maintenance` forbids a deployment lookup. Do you agree? What would you change, and what else would have to change with it?
5. What does the causal-claim check miss? Write a sentence that fools it.
6. Why is there no single score? What would you lose by adding one?
7. You changed the prompt until all six cases passed. What have you shown, and what have you not?
8. Where would a model judge help here, and what would you need before trusting it?
9. The runtime allows a deployment lookup for a healthy service: it is a valid, harmless call. Why is it still a regression?

Short guidance: tests state contracts, and this regression is not one (1); the status-first rule is architecture, everything else is behavior that may be reached many ways (2); the trajectory shows what was inspected and why, which the answer hides (3); it is a judgment call — the point is that it is written down and can be argued with, and changing it means changing the case and the version (4); a paraphrase like "this is on the deployment", or a hedge in the same sentence (5); a number cannot say which failure matters, and it hides fabricated claims among passes (6); that the system passes the cases you tuned on — not that it is robust to ones you did not write (7); clarity or completeness, after calibrating it against people who own the domain (8); the runtime guards execution, not quality — an allowed call can still be an unwanted one (9).

## Implementation decisions

- **A tiny deterministic harness, local to this lab.** One runner, one check class, one report. It is not a framework, and nothing in it is meant to be reused: a general-purpose evaluation library would be a different project, and the tools that exist for that job are not the subject of this handbook.
- **Cases are plain Java records.** No JSON, no JSONL, no parser. See "Build an evaluation set".
- **No new dependency.** The evaluation harness is plain Java on the JDK. The only production dependency is the Google Gen AI SDK, carried over from Labs 01–08 at the same version (1.72.0) for the optional live run only. A newer release exists (1.73.0); the labs stay aligned, and moving all of them is a compatibility update, not part of this milestone.
- **Local tools, not MCP.** The system under evaluation reuses the Lab 07 runtime with local tools. See "Starting point".
- **Two scripted versions, one deliberate regression.** The candidate has two flaws so that two different dimensions fail on different cases; it is a teaching regression, not a claim about how models fail.
- **A judge is explained, not implemented.** See "Deterministic checks vs model judges".
- **No ADR.** Using a small deterministic harness for this lab is a lab-level choice, not a rule later work must follow; the decision is recorded here. Evaluation examples that use frameworks are welcome later under the ecosystem track, where they clarify a concept.

## What we STILL do not have

- No evaluation of a real model's behavior committed to the repository — a live run is yours to make, and one run is not a benchmark
- No repeated runs, variance, or confidence intervals
- No model judge, no calibration against human grades, no annotation tooling
- No production-derived cases, no coverage measurement, no dataset splitting
- No stored history: a report is printed, not kept, so there is no trend across versions
- No observability — the trace is captured for a controlled run and printed; it is not telemetry
- No monitoring, alerting, or online evaluation of a running system
- No security testing of hostile inputs
- No evaluation of MCP-backed capabilities specifically, and no multi-agent or A2A evaluation

## What limitation remains?

After this lab, we can say something about how the system behaves on cases we chose in advance. That is controlled evidence about quality. But once the system runs for real, other questions arrive that no evaluation set answers:

- What actually happened on *this* request?
- Which model and which tools were called, in what order?
- Where was the time spent, and where did it fail?
- How often does a given behavior occur in production?
- How do I connect the events of one run, across a model call, a tool, and a remote server?

Evaluation gives controlled evidence about quality. Observability gives runtime evidence about what actually happened. That is **Milestone 10 — Observability**. Not implemented here.

## Sources consulted

Verified on 2026-09-30. The practices in this lab — the five dimensions, trajectory checks, baseline-vs-candidate comparison, the separation of tests from evaluations — are this handbook's own teaching design. The sources below support the general claims made about evaluation practice; they are not the definition of it, and no vendor's evaluation product is used or endorsed.

- Broader industry practice:
  - [Anthropic — Define your success criteria and build evaluations](https://platform.claude.com/docs/en/test-and-evaluate/develop-tests): success criteria that are specific and measurable, evals that mirror the real task distribution, edge cases such as ambiguous inputs, and code-graded, human-graded, and LLM-graded methods.
  - [OpenAI — Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices): eval-driven development, datasets that include typical, edge, and adversarial cases, the warning against datasets that do not reflect production traffic, judge bias (position, verbosity), calibrating automated grading with human feedback, and the progression from single-turn to workflows to agents.
  - Zheng et al., [Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena](https://arxiv.org/abs/2306.05685): the source for position, verbosity, and self-enhancement bias, and limited reasoning ability, in model judges, and for agreement with human preferences being measurable rather than assumed.
- Provider-specific, for the optional live mode only: [Function calling with the Gemini API](https://ai.google.dev/gemini-api/docs/function-calling) — function declarations, the model requests and the application executes, `gemini-3.8-flash` in the current examples. Gemini also supports several function calls in one turn; this runtime deliberately supports at most one per decision and stops with an explicit error otherwise, exactly as in Lab 07.
- [`google-genai` on Maven Central](https://central.sonatype.com/artifact/com.google.genai/google-genai): 1.73.0 is the latest release; this lab stays on 1.72.0 like Labs 01–08.
- Not cited on purpose: vendor evaluation platforms and observability products. Their feature lists are not evidence for how this lab works, and none is a dependency.
