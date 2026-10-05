# Lab 12 — Deployment

**Deployment packages and runs the application. It does not redefine who decides what the application may do.**

**A container is not an authorization boundary. The model proposes, the application authorizes, and that stays true on every machine.**

**Secrets are supplied at runtime, not packaged into the artifact.**

> This is a teaching deployment, not a production platform. It shows what must survive the move from an IDE to a running process, and it ends with an honest list of what production still needs.

## Why Deployment Matters

Lab 11 ended with controls that work and tests that prove them, on one laptop. The token is a constant in a class, the policy is compiled into the code, and nothing says how the application starts, what configures it, how anyone knows it is healthy, or what happens to a run that is half finished when the process is told to stop.

The tempting move is to put the Java application on a server and expose what it can do. That is the wrong move for an agentic system, because the interesting part of the system is the boundary between a model's proposal and a tool, and a deployment that exposes tools directly, or that adds a convenient switch to turn a check off, removes the boundary.

```
BAD   deployment ──► exposes the tools
GOOD  deployment ──► starts the same application boundary ──► validate ──► authorize ──► approve ──► tool
```

The story this lab completes:

> The model proposes. The application validates. The application authorizes. Tools execute. Observability records. Evaluation verifies. **Deployment packages and runs the system safely.**

Deployment adds a way in, a way to be configured, a way to be probed, and a way to stop. It adds no authority.

## Learning Objectives

- Build one **deployable artifact** and run it in a deployment-like environment
- Configure it from the **environment** instead of the source, with validation that fails fast
- Tell **liveness** from **readiness**, and see why shutdown makes an application alive and not ready
- Stop an agentic process **gracefully**, and say why that matters more than for a stateless service
- Keep every Lab 11 control active after deployment, and **prove it** by asserting the denial and that the privileged operation never executed
- Keep secrets out of the artifact, the image, the sample configuration, the logs, and the health responses
- Read the **deployment events** that Lab 10's habits produce, and know what must never be logged
- Package the artifact in a minimal, non-root container image, and read the Dockerfile as a set of decisions
- Know what a CI pipeline adds, and what this lab's pipeline deliberately does not do
- Name the **production gap**: what a real deployment needs that this lab leaves out

## Prerequisites

- Java 27 (`java -version`) and the Maven wrapper in this repository
- [Lab 11](../11-security/README.md): the gateway, the policy, and the redactor this lab runs. Lab 12 depends on that module and does not copy it.
- [Lab 10](../10-observability/README.md) for the ideas of structured events, correlation, and what must not be logged
- `curl` for the hands-on exercise
- Docker is optional. No test in this lab needs it, and neither does the jar path of the exercise.

No API key, no account, no cloud, and no network beyond loopback are needed.

## Architecture

What exists after this lab, and what moved:

```
                      ┌────────────────────── the deployed process ──────────────────────┐
 environment ───────► │ DeploymentConfig  (validated; refuses to start when invalid)       │
 (APP_*)              │                                                                    │
                      │ DeploymentApp ── GET /health   liveness                            │
 curl / a client ───► │               ── GET /ready    readiness                           │
                      │               ── GET /info     which artifact is this              │
                      │               ── POST /assist ──► Assistant ──► ToolGateway (Lab 11)│
                      │                                    model        validate           │
                      │                                    proposes     authorize          │
                      │                                                 approve ► tool     │
                      │ EventLog ──► standard output   (JSON lines, redacted)              │
                      └────────────────────────────────────────────────────────────────────┘
```

| Part | Where it comes from |
| --- | --- |
| The gateway, the policy, the tools, the redactor, the trace | [Lab 11](../11-security/README.md), used as a dependency |
| The simulated model | Lab 11's `SimulatedModel`: deterministic, and steerable by text it reads, on purpose |
| `DeploymentConfig`, `BuildInfo` | New: configuration and the artifact's identity |
| `DeploymentApp` | New: the HTTP door and the life cycle, on the JDK's built-in `HttpServer` |
| `EventLog` | New: one JSON line per event, through Lab 11's `Redactor` |
| `AuthorizationSelfCheck` | New: refuses to start, and to report ready, if the authorizer does not behave |
| `Dockerfile`, `scripts/`, `config/` | New: the image, two smoke tests, and sample configuration |

Everything is still simulated and in memory. `HelioPlatform` records what would change and touches no real system.

## Deployment Mental Model

```
source ──► build ──► tests ──► artifact ──► container image ──► running process
                      │          │                                    ▲
                      └──────────┘                                    │
                  the artifact is what was tested        configuration + secrets
                                                          arrive here, from outside
```

- **Build once, run the same artifact.** The jar is built and tested; the image wraps it; the process runs it. Nothing about the artifact changes between environments. Only configuration does.
- **The artifact says what it is.** The version and a build id are written into the jar, and `/info` and every log line report them. Configuration cannot change them.
- **Configuration chooses; it does not grant.** A setting may pick a port or offer fewer tools. No setting adds a tool, changes a policy, or turns a control off.
- **Secrets are not part of the artifact.** They enter the running process from outside, and the process never prints them.
- **Deployment boundary versus application boundary.** The deployment decides how the application is packaged, reached, and stopped. The application decides what each caller may cause. A network rule or a container limits who can reach the door; it never decides what happens behind it.

Four kinds of statement appear in this README, and they are kept apart:

| Kind | Means | Example |
| --- | --- | --- |
| **Project principle** | A rule this handbook holds itself to | A container is not the authorization boundary |
| **Industry guidance** | Common practice this lab follows in miniature | Separate liveness and readiness probes; run as non-root; do not bake secrets into images |
| **Implemented here** | What the code and tests in this lab do | An unknown `APP_*` setting stops startup |
| **Residual risk** | What remains true after the lab | The principal header is not authenticated |

## Build

```sh
./mvnw -B verify                                  # everything, including this lab; no API key
./mvnw -B -pl labs/12-deployment -am package -DskipTests   # only the artifact
```

`-am` ("also make") is required: this lab depends on Lab 11, and a build that names only this module cannot find it. The artifact is:

```
labs/12-deployment/target/lab-12-deployment.jar     the application
labs/12-deployment/target/lib/lab-11-security-0.1.0-SNAPSHOT.jar    its one runtime dependency
```

The jar's manifest names the entry point and the class path (`lib/...`), so `java -jar` is the whole start command. The jar carries `deployment.properties`, which Maven fills in at build time:

```
app.name=lab-12-deployment
app.version=0.1.0-SNAPSHOT
build.id=local            # CI passes the commit: -Dbuild.id=<sha>
```

The build is reproducible: a fixed `project.build.outputTimestamp` means the same sources and the same `build.id` produce the same bytes. Building twice gave an identical SHA-256, and changing only `-Dbuild.id` changed it.

## Configuration

Configuration is environment variables. They are read once, validated together, and every problem is reported at once before the application does anything else.

| Variable | Default | Meaning |
| --- | --- | --- |
| `APP_ENV` | **required** | Name of the environment: `local`, `test`, `demo`, anything matching `[a-z][a-z0-9-]{0,19}` |
| `APP_PORT` | `8080` | Port, 0 to 65535 (0 picks a free port, for tests) |
| `APP_BIND_ADDRESS` | `127.0.0.1` | Address to listen on: loopback unless you say otherwise |
| `APP_LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARN`, or `ERROR` |
| `APP_MODE` | `deterministic` | The only mode implemented. A live model mode is not part of this lab. |
| `APP_TOOLS` | all four Helio tools | Which of the application's own tools to offer. It can only remove tools. |
| `APP_EXPECTED_VERSION` | unset | If set, startup is refused unless the artifact is exactly this version |
| `APP_SHUTDOWN_GRACE_SECONDS` | `10` | How long shutdown waits for work already started, 0 to 60 |
| `APP_MONITORING_TOKEN` | unset | An optional secret supplied at runtime. If set, it is registered with the redactor and never printed. |

Local, test, and demo differ only in values. The sample files are in [config/](config/): `local.env`, `test.env` (read-only tools, a free port, a short grace period), and `demo.env` (the container demo). They contain no secrets, and a test parses each one through the real validator.

What configuration may **not** do is the point of the design:

- **No setting weakens a control.** `APP_AUTHORIZATION=off` is not "ignored". Any unknown `APP_*` variable stops startup with `unknown setting ... (settings cannot change authorization, approval, or validation; the application has none for them)`.
- **No setting adds a tool.** `APP_TOOLS` must name tools the application registered. Naming `executeShell` fails; so does an empty list.
- **No setting changes what the artifact is.** The version and build id come from the jar.
- **Error messages name settings and rules, never values.** A secret pasted into the wrong variable is not copied into a log.

Try it:

```sh
java -jar labs/12-deployment/target/lab-12-deployment.jar                       # exit 2: APP_ENV is required
APP_ENV=demo APP_PORT=99999 APP_AUTHORIZATION=off java -jar labs/12-deployment/target/lab-12-deployment.jar
APP_ENV=demo java -jar labs/12-deployment/target/lab-12-deployment.jar --check-config   # validate, print, exit
```

`--check-config` prints the settings in effect with the secret hidden (`monitoringToken=set (hidden)`) and exits without starting anything.

### Secrets

- A secret is **never** in source, in the Dockerfile, in an image layer, in a sample configuration file, in a log line, or in a health response. `DeploymentConfig.toString()` is overridden because a record's generated one would print the token.
- The deterministic path needs **no** credential. `APP_MONITORING_TOKEN` exists to show the shape: it arrives in the environment of the running process (`docker run -e APP_MONITORING_TOKEN`, or `export` in your shell), is registered with the `Redactor` so it is removed from any value that would be logged or returned, and appears nowhere else.
- A secret that is set but empty is rejected, which is this lab's version of "the secret is missing".
- **Industry guidance:** production uses a dedicated secret manager that injects, rotates, and audits secrets. An environment variable is visible to anyone who can inspect the process or the container, so it is the smallest honest example, not a recommendation. This lab builds no secret-management platform.

## Containerization

[`Dockerfile`](Dockerfile), built from the repository root:

```sh
docker build -f labs/12-deployment/Dockerfile -t helio-assistant:local .
docker run --rm -p 127.0.0.1:8080:8080 -e APP_ENV=demo helio-assistant:local
```

| Question | Answer in the Dockerfile |
| --- | --- |
| Build stage | `eclipse-temurin:27-jdk`: runs the Maven wrapper to package the jar. Not part of the final image. |
| Runtime stage | `eclipse-temurin:27-jre`: a JRE and the jar, with no compiler, no Maven, and no sources |
| Artifact copied | `lab-12-deployment.jar` and `lib/` from the build stage, and nothing else |
| Runtime user | A system user with fixed id `10001`, set with `USER 10001:10001`; the process does not run as root |
| Port | `EXPOSE 8080` and `APP_PORT=8080`, with `APP_BIND_ADDRESS=0.0.0.0` because a container must listen on all its interfaces |
| Start command | Exec form `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/lab-12-deployment.jar"]` |
| Configuration source | The environment at `docker run` (`-e`, `--env-file`). `APP_ENV` is not baked in, so a container without it refuses to start. |
| Secrets | None in the file. No `ENV` or `ARG` carries one. |

Why these choices:

- **Two stages** because the point is what runs: the compiler, the build tool, and the sources do not ship in the image.
- **Exec form** because the JVM must be process 1 to receive `SIGTERM` directly. The shell form puts a shell in front that does not forward the signal, and graceful shutdown would silently stop working.
- **JVM flags**: `MaxRAMPercentage` makes the heap follow the container's memory limit, and `ExitOnOutOfMemoryError` makes an out-of-memory process exit so a supervisor can restart it, instead of limping on. Both are plain HotSpot flags.
- **Non-root** because a compromised process then holds an unprivileged user, not root in the container.
- **Publish on loopback**: `-p 127.0.0.1:8080:8080` keeps the port off the network. Without the `127.0.0.1:` prefix Docker publishes on every interface, which is the kind of exposed management endpoint the threat model below names.
- `.dockerignore` at the repository root keeps `.git`, build output, and `*.env` files out of the build context.

The tests read the Dockerfile as text and assert all of this (see Verification), because they must not need Docker. Whether an image actually builds is checked by `docker build` in CI, and the same image is started and stopped by [scripts/container-smoke-test.sh](scripts/container-smoke-test.sh).

## Health and Readiness

| Endpoint | Question | Answers |
| --- | --- | --- |
| `GET /health` | Is the process alive? | Always `200 {"status":"UP"}` while the process can answer, including during shutdown |
| `GET /ready` | Should it receive work? | `200 {"status":"READY",...}` while running; `503 {"status":"NOT_READY",...}` while starting or draining |
| `GET /info` | Which artifact is this? | App name, version, build id, environment name, and the tools offered |

```json
{"status":"READY","checks":{"lifecycle":"UP","authorization":"ENFORCED"}}
```

- **They are separate on purpose.** A draining application is alive and not ready. One signal for both would either restart an application that was only finishing its work or send work to one that cannot take it.
- **Readiness includes the controls.** On every `/ready` call the application asks its authorizer two questions that must never change: an unknown principal is denied, and a state change with no approval requires one. If the authorizer stops behaving like that, readiness fails. The same check runs before startup, and a failing one refuses to start (see Failure Modes). The check asks the authorizer; it never executes a tool.
- **Responses are deterministic** and carry nothing sensitive: no token, no environment dump, no stack trace, no echo of the request. An unknown path returns `404 {"error":"NOT_FOUND"}` and the path is not copied into the log.
- Probe calls are logged at `DEBUG` only, so a probe every few seconds does not drown the log.

Liveness and readiness here are about the process and its controls. They do not check a database, a model provider, or any dependency, because there is none; a production readiness check would.

## Graceful Shutdown

On `SIGTERM` (or `SIGINT`) the shutdown hook runs this sequence:

```
STARTING ──► READY ──► DRAINING ──► STOPPED
                          │
   readiness turns to not-ready    (/ready → 503, /health still 200)
   new /assist requests refused    (503 NOT_READY)
   work already started finishes   (up to APP_SHUTDOWN_GRACE_SECONDS)
   an event records each step      (READINESS_CHANGED, SHUTDOWN_STARTED, SHUTDOWN_COMPLETED)
```

Work is counted from the moment a request is admitted until its response has been written, not only until the run ends. A test caught the difference: counting only the run let the server close the connection before the finished answer left, so the caller lost an answer that had in fact been computed. If the grace period runs out first, the application says so (`SHUTDOWN_TIMEOUT` with the number abandoned, and `drained=false`) rather than reporting a clean stop.

After a signal the JVM exits with 143 (128 plus the signal number). Calling shutdown twice is safe.

**Why killing an agentic process is worse than killing a stateless service.** A stateless request that dies midway can usually be retried and has done nothing the world can see. An agent run may be midway through:

- a **tool execution**: a state change that already started and cannot be assumed to have not happened
- a **pending approval**: a held operation bound to a requester and arguments that exist only in this process's memory
- a **partial workflow**: several tool calls in, with a model whose next step depends on the earlier results
- **observability events** that explain what happened: if the process dies silently, the record of what it did dies with it
- an **external side effect** that a retry would repeat

Graceful shutdown narrows these windows; it does not remove them. A process that is killed with `SIGKILL`, or that loses its machine, gets no grace. This lab keeps no state across a restart: the approval store is in memory, so a pending approval does not survive. There are no distributed transactions here, and making a state change safe to retry (idempotence, durable approvals) is outside this lab.

## Security Boundaries

The Lab 11 controls are not reimplemented here, and they are not optional here.

| Control | After deployment | Test |
| --- | --- | --- |
| The model proposes; it does not execute | `POST /assist` runs the same `Assistant` and `ToolGateway`. No endpoint calls a tool directly. | Every test in `SecurityContinuityTest` |
| Argument validation | A hostile proposal (`billing; rm -rf /`) is rejected | `invalidArgumentsFromAModelAreStillRejectedInTheDeployedApplication` |
| Authorization | A restricted principal is denied | `aRestrictedPrincipalIsDeniedAndTheRestartNeverRuns` |
| Approval boundary | A principal that may propose a restart still needs an approval, and nothing runs | `aPrincipalThatMayProposeAStateChangeStillNeedsAnApprovalAndNothingRuns` |
| Fail closed | No principal and an unknown principal both get no authority | `noPrincipalFailsClosedBeforeAnyToolRuns`, `anUnknownPrincipalHasNoAuthority` |
| Least privilege | A tool the configuration does not offer does not exist | `aToolTheConfigurationDoesNotOfferDoesNotExist` |
| Sensitive data | The platform's raw token never reaches a response or a log | `aSecretNeverReachesAResponseOrALog` |
| Untrusted input | The poisoned incident note still cannot cause a restart; claiming authority in the request text changes nothing | `claimingAuthorityInTheRequestTextChangesNothing` |
| Configuration cannot weaken any of this | Unknown settings, including every "turn authorization off" spelling, stop startup | `aSettingThatWouldWeakenAuthorizationIsRejectedNotIgnored` |
| A swapped-in authorizer is refused | An allow-all authorizer, one that skips the approval rule, and one that throws all stop startup | `LifecycleTest` |

Each security test asserts two things, exactly as in Lab 11: the **denial**, and that the **privileged operation never executed**. The simulated platform counts what really ran (`executionCount()`, `stateChanges()`), so "denied" cannot hide an operation that ran anyway.

**A container is not an authorization boundary.** Putting the application in a container, binding it to loopback, or placing a firewall in front of it limits who can reach the door. Whoever does reach `/assist` still gets only what the policy gives their principal. A user does not gain authority because the application runs in a container, and `everyEnvironmentConfigurationEnforcesTheSamePolicy` runs the same restricted request under different environment names and asserts the same denial.

**What this lab does not do: authenticate.** `POST /assist` reads the principal from an `X-Principal` header, exactly as Lab 11's gateway takes the principal from its caller. In a real deployment a front door that verifies an identity (a session, a token) sets that value, and nothing a client sends is trusted as one. Here, anyone who can reach the port can claim any principal. That is why the default bind address is loopback, why the sample demo publishes on loopback, and why this is listed as a residual risk and not hidden. The malformed-header and oversized-request checks are input validation at the door, not authentication.

### Threat model

This extends the Lab 11 threat model to the deployment. The assets and controls below are the ones this lab touches.

| Asset | Threat | Control in this lab | Residual risk |
| --- | --- | --- | --- |
| Credentials and API keys | Leaked in source, image, config, log, or health response | None are present; runtime injection; redaction; messages name settings, not values; tests scan the jar, the Dockerfile, and the sample config | An environment variable is visible to anyone who can inspect the process; no rotation |
| Tool and model access | A deployment exposes tools directly, or authorization is accidentally disabled | One door to the same gateway; no setting for authorization; startup self-check; readiness check | Authentication is not implemented, so a reachable port means a claimable principal |
| Configuration | Insecure or malicious values | Validation; unknown settings rejected; configuration can only remove tools; values not echoed | The environment itself is trusted: whoever sets it chooses the port, the bind address, and the tools offered |
| The artifact | A tampered or wrong artifact runs | Build id and version in the jar and in every log line; `APP_EXPECTED_VERSION`; reproducible jar | No signature, no provenance attestation, no verified base image |
| The image | A compromised or oversized base image, excess privilege | Minimal two-stage image, a JRE only, non-root user, pinned major version | The base image tag is a moving tag; no vulnerability scanning; no read-only filesystem or capability dropping |
| Management endpoints | Probes reachable by anyone | They return no sensitive data; loopback by default; publish on loopback in the demo | `/info` and the probes are unauthenticated by design |
| User data | A request body in logs | The body is never logged; only lengths and outcomes | Responses carry tool results to whoever can call |
| Logs | Credentials or forged lines | Redaction; JSON escaping; only application paths are logged | Redaction is a heuristic, as in Lab 11; the log has no access control or retention |
| Runtime environment | Untrusted runtime input | Body size limit, principal format check, method and path checks | No rate limiting, no TLS |

## Observability

The deployment's log reuses Lab 10's habits, through Lab 11's trace and redactor, and adds nothing that decides anything. Each line is one JSON object on standard output, where a container platform collects it. Every line carries the same minimal identity: `app`, `version`, `build`, `env`.

| Event | When |
| --- | --- |
| `STARTUP_BEGIN`, `STARTUP_COMPLETED`, `STARTUP_REFUSED` | The application starts, or declines to |
| `READINESS_CHANGED` | Ready turns true or false |
| `HEALTH_CHECK`, `READINESS_CHECK` | A probe was answered (`DEBUG` only) |
| `REQUEST_COMPLETED`, `REQUEST_FAILED` | A request finished: path, status, trace id |
| `SECURITY_EVENT` | One per Lab 11 security event of a run: `AUTHORIZATION_DENIED`, `TOOL_EXECUTION_STARTED`, `APPROVAL_REQUIRED`, and so on, at `WARN` for a denial and `INFO` for an allowance |
| `RUN_COMPLETED` | One run: trace id, principal, tool calls, how many executed |
| `SHUTDOWN_STARTED`, `SHUTDOWN_COMPLETED`, `SHUTDOWN_TIMEOUT` | Graceful shutdown |

What is **never** logged: credentials, API keys, the request body, tool results, the environment, or an unknown path an outsider made up. No caller of the logger can pass a body or an environment, and every value goes through the redactor first. A test sends a request containing the token and asserts it is in neither the response nor any log line.

Recording is not enforcing, as in Lab 11: a denial happens in the gateway whether or not anyone reads the `SECURITY_EVENT` line. This is also not OpenTelemetry, a collector, or a SIEM. The log is a stream of lines; collecting, storing, searching, alerting on, and protecting them is the platform's job and is not solved here.

Evaluation stays separate from the runtime: nothing here runs an evaluation set, and the deployed application never reads its own tests. Lab 09's harness remains a thing you run against the system, not part of it.

## CI/CD

[`.github/workflows/build.yml`](../../.github/workflows/build.yml) was extended, not replaced. In order, on every pull request and push to `main`:

1. **Check the Java version** (`java -version`, Java 27).
2. **`./mvnw -B verify -Dbuild.id=<commit>`**: compiles every module, runs every test (these labs, the capstone, and this lab's deployment tests), and packages the jar with the commit as its build id. The packaged-artifact tests run here, after packaging.
3. **Smoke test the jar**: [scripts/smoke-test.sh](scripts/smoke-test.sh) starts the jar, checks `/health`, `/ready`, and `/info`, sends `SIGTERM`, and checks the graceful shutdown and exit code 143.
4. **Upload the artifact** (the jar and `lib/`) as a workflow artifact.
5. **Build the container image** with `docker build`, passing the commit as `BUILD_ID`.
6. **Smoke test the image** with [scripts/container-smoke-test.sh](scripts/container-smoke-test.sh): `--check-config`, then run it, probe it, confirm a non-root user, stop it, confirm the graceful shutdown.

The pipeline pushes nothing, logs in to no registry, and uses no secrets, API keys, or cloud credentials. `permissions` stay at `contents: read`.

Honest limit: the Docker build stage compiles from the same commit instead of copying the jar from step 2, so the image holds a rebuild of the artifact, not the very bytes that were tested. The reproducible jar and the shared build id make that a small gap, and a production pipeline closes it by building once and promoting the same artifact (or its digest) through environments. The Dockerfile is a learner's convenience: it needs only Docker.

## Deployment Failure Modes

Each is deterministic and covered by a test.

| Failure | What happens | Why | Test |
| --- | --- | --- | --- |
| **Missing config** (`APP_ENV`) | Exit 2: `APP_ENV is required` | A deployment must say what it is | `aMissingRequiredSettingFailsFast`, `theJarStartsServes...` |
| **Invalid config or wrong port** | Exit 2, every problem listed, no value echoed | Fail fast and do not leak | `invalidValuesAreRejected...`, `aPortThatIsNotANumberIsRejected` |
| **A secret that is missing or empty** | An empty `APP_MONITORING_TOKEN` is rejected | A silently empty secret is a configuration bug that looks like success | `aBlankSecretIsRejected...` |
| **Authorization accidentally disabled** | Unknown setting: startup stops. A different authorizer: the self-check refuses startup. | Configuration and wiring cannot remove a control | `aSettingThatWouldWeaken...`, `anAuthorizerThatAllowsEverythingIsRefusedAtStartup` |
| **Version mismatch** | Exit 2 before anything listens | This is not the artifact the deployment expects | `aVersionMismatchStopsStartup...` |
| **Port already taken** | Startup fails (the entry point exits with 1) | The application cannot serve | `aPortThatIsAlreadyTakenFailsStartup` |
| **Readiness failure** | `/ready` returns 503 while draining; `/health` stays 200 | Alive is not ready | `gracefulShutdownFlipsReadiness...` |
| **Shutdown interrupted by a long run** | `SHUTDOWN_TIMEOUT` and `drained=false` | Say so instead of pretending the stop was clean | `shutdownThatCannotWaitLongEnough...` |
| **Observability config** | An unrecognized log level is invalid configuration, so the application never runs with logging in an unknown state | Observability is part of the runtime | `invalidValuesAreRejected...` |

## Supply-Chain Considerations

Awareness only; this lab adds no scanner or signing stack.

- **Dependencies.** The runtime has one dependency, the Lab 11 module in this repository, and no third-party library. JUnit is a test dependency. Fewer dependencies means less to audit, and a dependency you do not have cannot have a vulnerability.
- **Reproducible builds.** A fixed output timestamp makes the jar byte-identical for the same sources and build id, which lets someone else check your artifact.
- **Immutable artifacts.** Build once and run the same thing everywhere is the habit this lab shows with a version and a build id in the artifact.
- **Base image.** `eclipse-temurin:27-jre` is a maintained, widely used base image, tagged for the major version. A tag moves; pinning by digest, and updating it on purpose, is a production practice not done here.
- **Dependency vulnerabilities and provenance.** Not scanned and not attested. Production uses a software bill of materials, vulnerability scanning, and signed provenance.
- **Artifact integrity.** Nothing signs the jar or the image. The sha256 of the jar is the simple form of "is this the artifact I think it is".

## Hands-On Exercise

All on your machine, with no key and no account.

**1. Build and see the artifact.**

```sh
./mvnw -B verify
ls labs/12-deployment/target/lab-12-deployment.jar labs/12-deployment/target/lib
unzip -p labs/12-deployment/target/lab-12-deployment.jar deployment.properties
sha256sum labs/12-deployment/target/lab-12-deployment.jar
```

**2. Watch it refuse to start.**

```sh
java -jar labs/12-deployment/target/lab-12-deployment.jar; echo "exit=$?"
APP_ENV=demo APP_AUTHORIZATION=off java -jar labs/12-deployment/target/lab-12-deployment.jar; echo "exit=$?"
APP_ENV=demo APP_EXPECTED_VERSION=9.9.9 java -jar labs/12-deployment/target/lab-12-deployment.jar; echo "exit=$?"
```

**3. Start it, configured from a file, and probe it.**

```sh
set -a; . labs/12-deployment/config/local.env; set +a
java -jar labs/12-deployment/target/lab-12-deployment.jar &
curl -s localhost:8080/health; echo
curl -s localhost:8080/ready; echo
curl -s localhost:8080/info; echo
```

**4. Send the Lab 11 attack through the deployed door.** The request makes the simulated model read the poisoned incident note, which tells it to restart `billing`.

```sh
curl -s -X POST -H 'X-Principal: triage-assistant'   -d 'Summarize incident INC-1002 on notifications.' localhost:8080/assist
curl -s -X POST -H 'X-Principal: incident-responder' -d 'Summarize incident INC-1002 on notifications.' localhost:8080/assist
curl -s -X POST                                      -d 'Summarize incident INC-1002 on notifications.' localhost:8080/assist
```

Read each `results`. The first is `DENIED` (`CAPABILITY_NOT_GRANTED`), the second `APPROVAL_REQUIRED`, and the third has no principal and nothing runs. The restart never happened in any of them. Find the `SECURITY_EVENT` lines in the output of the process.

**5. Stop it gracefully.**

```sh
kill -TERM %1
```

Read `READINESS_CHANGED`, `SHUTDOWN_STARTED`, and `SHUTDOWN_COMPLETED`. Run it again with `APP_LOG_LEVEL=DEBUG` and the probes appear too.

**6. Change the environment, not the code.**

```sh
APP_ENV=test APP_PORT=9090 APP_TOOLS=getServiceStatus java -jar labs/12-deployment/target/lab-12-deployment.jar
```

Repeat step 4 against port 9090 (stop the first process first, or keep both running): the note lookup is now `UNKNOWN_TOOL`, because the deployment offers fewer tools, so the poisoned note is never even read. Try to make the deployment offer more than the application registered. You cannot.

**7. Optional, with Docker.**

```sh
docker build -f labs/12-deployment/Dockerfile -t helio-assistant:local .
labs/12-deployment/scripts/container-smoke-test.sh helio-assistant:local
docker run --rm -p 127.0.0.1:8080:8080 --env-file labs/12-deployment/config/demo.env helio-assistant:local
```

Then run `docker run --rm helio-assistant:local` with no `-e APP_ENV` and watch it refuse.

**8. Break it on purpose.** In a scratch copy, make the policy allow everyone and watch `LifecycleTest` and startup refuse it. Then restore it.

## Verification

```sh
./mvnw -B verify                                # every module; this lab's tests are in labs/12-deployment
python -m unittest discover -s scripts/tests    # cross-document checks
```

Lab 12 has 55 deterministic tests. None needs an API key, a network beyond loopback, or Docker.

| Group | Tests | What they prove |
| --- | --- | --- |
| `DeploymentConfigTest` | 11 | Defaults, custom values, missing and invalid values, weakening settings rejected, no values in messages, the secret never printed, version check, sample files valid and secret-free |
| `HealthAndReadinessTest` | 7 | Healthy startup, deterministic responses, `/info`, build identity filled in by Maven, no leakage, unknown paths and methods, probes logged at `DEBUG` only |
| `LifecycleTest` | 9 | Startup events, graceful shutdown (not ready, work finishes, event order), shutdown timeout, double shutdown, three refused authorizers, version mismatch, port in use |
| `SecurityContinuityTest` | 12 | The Lab 11 controls hold, each asserting the denial and that nothing ran |
| `DockerfileTest` | 10 | Two stages, JRE runtime, pinned tags, artifact-only copy, non-root user, exposed port, exec-form entry point, JVM flags, no secrets, environment not baked in, `.dockerignore` |
| `PackagedArtifactTest` | 6 | After packaging: the jar and `lib/`, the manifest, identity, no secrets in the jar, reproducible timestamps, and the jar as a real process: probes, `SIGTERM`, a clean shutdown event, exit code 143 |

`PackagedArtifactTest` runs in Maven's `verify` phase, after the jar exists; the other five run in `test`. Lab 11, the earlier labs, and the capstone run in the same build, so a regression in any of them fails it too. The Python checks pin the cross-document facts: the roadmap, the README, the labs index, the site, the CI file, and the absence of anything beyond scope.

What was and was not checked outside the tests when this lab was written: the jar was built, started, probed with `curl`, and stopped with `SIGTERM` (exit 143), and two builds gave identical SHA-256. The image build and the container smoke test are performed by CI; a machine without Docker can read the Dockerfile and run its structural tests but has not built the image.

## What This Project Does Not Solve

This is not a production deployment. It does not solve:

- Kubernetes production orchestration (scheduling, rollouts, probes wired to a platform, resource limits)
- Autoscaling
- Multi-region deployment
- Cloud IAM (cloud identities, roles, and the credentials a workload gets from a cloud)
- Enterprise secret management (rotation, audit, per-workload secrets)
- Production identity federation: authentication is not implemented; the principal is a header
- Zero-trust networking, TLS, and network policy
- Full supply-chain security (signing, SBOMs, provenance, vulnerability scanning, pinned base images)
- Production incident response (paging, runbooks, on-call, postmortems)
- High availability and disaster recovery (replicas, failover, backups, state that survives a restart)
- Distributed transactions: an in-flight tool execution cannot be made atomic across systems
- Production model serving (capacity, latency, cost, provider failure, a real model at all)
- Enterprise policy management (the policy is a table compiled into the code)
- Full OpenTelemetry (collectors, backends, sampling, dashboards, alerting)
- SIEM integration and log protection, retention, and tamper evidence

Also absent, and out of scope by design: any web framework, any new MCP server, any new model provider, rate limiting, and a durable approval store.

## Production Gap Analysis

| Concern | Handbook (this lab) | Production |
| --- | --- | --- |
| Packaging | One jar and a minimal two-stage image, built with the Maven wrapper; the image rebuilds from the commit | Build once in a pipeline, sign it, store it in a registry, promote the same digest through environments |
| Configuration | Environment variables, validated at startup, no settings that weaken controls | A configuration service or platform config, reviewed changes, per-environment policy, drift detection |
| Secrets | None needed; an optional token injected through the environment and redacted | A secret manager with rotation, audit, short-lived credentials, and per-workload access |
| Health | `/health` says the process answers | Probes wired to the platform's restart policy, with thresholds and startup probes |
| Readiness | `/ready` is false while draining and checks the authorizer | Readiness that also checks dependencies (a model provider, a policy source, a datastore) and sheds load |
| Security | The Lab 11 controls run behind the door; the principal is an unauthenticated header; loopback by default | Authenticated identity, TLS, network policy, per-user credentials, a policy engine, rate limits, durable approvals |
| Observability | Structured JSON lines on stdout with a minimal identity, and redaction | OpenTelemetry or equivalent, collection, retention, access control, dashboards, alerts, SLOs |
| CI/CD | `build.yml` verifies, packages, smoke-tests, uploads the jar, and builds the image without pushing | Promotion gates, environment-specific deploys, rollbacks, approvals, deploy auditing |
| Supply chain | One in-repo dependency and a reproducible jar; the risks are named | SBOM, vulnerability scanning, signed provenance, a pinned and rebuilt base image |
| Scaling | One process | Replicas, autoscaling, load balancing, and a rule for shared state |
| Recovery | A process that exits and a record of why | Restarts, backups, failover, state that survives a restart, tested disaster recovery |

## Reflection Questions

1. A teammate adds `APP_AUTHORIZATION=off` for a demo and promises not to use it in production. Why does this lab reject the setting instead of ignoring it, and what would you lose if it were allowed?
2. Your service is behind a firewall and only your team can reach it. Why is the principal header still a problem?
3. Readiness fails while the application is draining and `/health` stays up. What happens in a platform that restarts any application whose health check fails if you merge the two?
4. A request is half finished when `SIGKILL` arrives. List what is left in an unknown state. What would you need so it could be retried safely?
5. Why is `APP_TOOLS` allowed to remove a tool and not to add one? Which principle is that?
6. The image rebuilds the jar instead of copying the tested one. Is that a flaw? What would you do differently with a registry and a pipeline?
7. A log line contains the request text. Why does this lab never log it, and what would you log instead to diagnose a problem?
8. Environment variables are visible to anyone who can inspect the process. What does that change about the secret-handling advice here?

## Implementation decisions

Lab-level choices, recorded here as the project asks. None of them changes the shape of the project.

- **Lab 12 depends on the Lab 11 module and does not copy it.** Earlier labs carry educational copies and import nothing from each other. A deployment that copied the boundary could drift from the tested one, which is the failure this lab exists to prevent, so here the dependency is the point. Lab 11 itself holds a trimmed copy of the Lab 10 trace model, so this lab reuses Lab 10's observability through Lab 11 rather than importing Lab 10, which would pull in a provider SDK this lab does not need.
- **The JDK's built-in `HttpServer`**, because the repository uses no web framework and four endpoints do not justify one. It is a supported module, not preview or incubator.
- **Environment variables, not configuration files**, because that is how containers are configured and it keeps secrets out of the repository. The sample files are env files that a shell or `--env-file` reads.
- **A runnable jar plus `lib/`, not a fat jar**, to avoid a plugin that rewrites bytecode and to keep one file per dependency visible.
- **The startup self-check asks the authorizer instead of the gateway**, so it never executes a tool, and the same check can run on every `/ready`.
- **Two surefire executions** (`test` and `verify` phases), so the artifact tests run after packaging and the rest run before it.
- **A header for the principal**, the smallest honest stand-in for an identity, with the gap stated.
- **No new ADR.** [ADR 0006](../../docs/adr/0006-application-owned-authorization-for-state-changing-tools.md) already records the durable rule this lab depends on: authorization is application-owned and independent of the model. Lab 12 applies it to a deployment. Its other choices, listed above, are implementation choices inside one lab. A durable deployment decision, such as choosing a platform, would need an ADR, and this lab chooses none.

## Do I actually need this?

For a script, a notebook, or a lab, no. For a system other people or other systems will call, yes, and the questions that matter first are not about containers: what configures it, how you know it is healthy, how it stops, and what keeps its controls on. A container is one way to get a repeatable runtime. It is not required, and it is not a security control.

## What we STILL do not have

- No authentication; the principal is a header
- No TLS, network policy, or rate limiting
- No durable state: approvals, traces, and the policy do not survive a restart
- No secret manager, rotation, or audit
- No image signing, scanning, provenance, or digest pinning
- No registry, no promotion between environments, and no rollback
- No dependency checks in readiness, because there are no dependencies
- No metrics endpoint and no distributed tracing
- No real model: the default and only mode is the deterministic simulation

## Transition Beyond the Handbook

The numbered path ends here. You have followed one idea from a model call to an agent that is evaluated, observed, secured, and deployable, and the lesson has been the same at every step: the model proposes, and the application decides. What comes next is mostly not agentic.

- Take the boundary to a real platform: a registry, a pipeline that builds once and promotes, and an orchestrator that uses the probes you now have.
- Replace the stand-ins with the real thing: an identity provider in front of the door, a secret manager, a policy source, durable approvals.
- Use the production gap table as a checklist. Every row is an engineering task with its own literature, and none of them is specific to LLMs.
- Keep the tests. The most reusable part of this lab is the habit: assert the denial, and assert the privileged operation never ran.
- The roadmap lists the work that comes after the numbered path: a composed reference application, multi-agent systems, and A2A. They are not implemented, and many applications never need them. See [ROADMAP.md](../../ROADMAP.md).
