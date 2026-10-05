package dev.agentic.handbook.labs.deployment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.agentic.handbook.labs.security.Assistant;
import dev.agentic.handbook.labs.security.AssistantModel;
import dev.agentic.handbook.labs.security.Authorizer;
import dev.agentic.handbook.labs.security.GatewayResult;
import dev.agentic.handbook.labs.security.HelioPlatform;
import dev.agentic.handbook.labs.security.HelioTools;
import dev.agentic.handbook.labs.security.PendingOperations;
import dev.agentic.handbook.labs.security.PolicyAuthorizer;
import dev.agentic.handbook.labs.security.Principal;
import dev.agentic.handbook.labs.security.Redactor;
import dev.agentic.handbook.labs.security.SecurityEvent;
import dev.agentic.handbook.labs.security.SimulatedModel;
import dev.agentic.handbook.labs.security.Span;
import dev.agentic.handbook.labs.security.SpanType;
import dev.agentic.handbook.labs.security.ToolDefinition;
import dev.agentic.handbook.labs.security.ToolGateway;
import dev.agentic.handbook.labs.security.Trace;
import dev.agentic.handbook.labs.security.TraceRecorder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The deployed application: the Lab 11 application boundary behind a small HTTP
 * door, with a life cycle.
 *
 * <p>Deployment adds a way in and a way to be started and stopped. It adds no
 * authority. A request to {@code /assist} becomes a run of the same
 * {@link Assistant} and the same {@link ToolGateway} that Lab 11 tests: the model
 * proposes, the gateway validates and authorizes, and only then does a tool run.
 * There is no endpoint that calls a tool directly, and no setting that skips the
 * gateway.
 *
 * <ul>
 *   <li>{@code GET /health}: liveness. The process is up. Stays 200 while shutting down.</li>
 *   <li>{@code GET /ready}: readiness. 200 only while the application accepts
 *       work and its authorizer still behaves; 503 while shutting down.</li>
 *   <li>{@code GET /info}: which artifact and environment this is.</li>
 *   <li>{@code POST /assist}: one run of the assistant. The caller names the
 *       principal in {@code X-Principal}. <b>This lab does not authenticate:</b>
 *       the header stands in for an identity that a real front door would verify.</li>
 * </ul>
 */
public final class DeploymentApp {

    static final int MAX_BODY_BYTES = 2000;
    private static final Pattern PRINCIPAL = Pattern.compile("[a-z][a-z0-9-]{0,39}");

    private final DeploymentConfig config;
    private final BuildInfo build;
    private final EventLog log;
    private final Redactor redactor;
    private final HelioPlatform platform = new HelioPlatform();
    private final Authorizer authorizer;
    private final AssistantModel model;
    private final ToolGateway gateway;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final Object lifecycle = new Object();
    private final Object assistLock = new Object();
    private final AtomicInteger runs = new AtomicInteger();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private State state = State.STARTING;
    private int inFlight;

    /** Starts the deterministic application with the Lab 11 policy. */
    public static DeploymentApp start(DeploymentConfig config, BuildInfo build, Consumer<String> logSink)
            throws IOException {
        return start(config, build, logSink, Clock.systemUTC(), new SimulatedModel(), PolicyAuthorizer.helioPolicy());
    }

    /**
     * The same start with its collaborators given. Tests use it to fix the clock, to hold a run
     * open, and to hand the application an authorizer that must be refused.
     */
    static DeploymentApp start(DeploymentConfig config, BuildInfo build, Consumer<String> logSink, Clock clock,
            AssistantModel model, Authorizer authorizer) throws IOException {
        config.checkAgainst(build);
        Redactor redactor = new Redactor(knownSecrets(config));
        EventLog log = new EventLog(build, config.environment(), config.logLevel(), redactor, clock, logSink);
        log.log(LogLevel.INFO, "STARTUP_BEGIN", "bindAddress", config.bindAddress(), "tools",
                String.join(",", config.enabledTools()));
        if (!AuthorizationSelfCheck.passes(authorizer)) {
            log.log(LogLevel.ERROR, "STARTUP_REFUSED", "reason", "AUTHORIZATION_SELF_CHECK_FAILED");
            throw new IllegalStateException("The authorizer failed its self-check; refusing to start.");
        }
        DeploymentApp app = new DeploymentApp(config, build, log, redactor, model, authorizer);
        app.begin();
        return app;
    }

    private DeploymentApp(DeploymentConfig config, BuildInfo build, EventLog log, Redactor redactor,
            AssistantModel model, Authorizer authorizer) throws IOException {
        this.config = config;
        this.build = build;
        this.log = log;
        this.redactor = redactor;
        this.model = model;
        this.authorizer = authorizer;
        Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        HelioTools.definitions(platform).forEach((name, tool) -> {
            if (config.enabledTools().contains(name)) {
                tools.put(name, tool);
            }
        });
        this.gateway = new ToolGateway(tools, authorizer, new PendingOperations(), redactor);
        this.server = HttpServer.create(new InetSocketAddress(
                java.net.InetAddress.ofLiteral(config.bindAddress()), config.port()), 0);
        server.createContext("/", this::dispatch);
        server.setExecutor(executor);
    }

    private void begin() {
        server.start();
        synchronized (lifecycle) {
            state = State.READY;
        }
        log.log(LogLevel.INFO, "READINESS_CHANGED", "ready", "true", "state", State.READY.name());
        log.log(LogLevel.INFO, "STARTUP_COMPLETED", "port", String.valueOf(port()));
    }

    // ---- observation, for the entry point and for tests --------------------------------------

    public int port() {
        return server.getAddress().getPort();
    }

    public State state() {
        synchronized (lifecycle) {
            return state;
        }
    }

    /** The simulated platform, so a caller can check what really executed. */
    public HelioPlatform platform() {
        return platform;
    }

    /** Blocks until shutdown has finished. */
    public void awaitStopped() throws InterruptedException {
        stopped.await();
    }

    // ---- shutdown ----------------------------------------------------------------------------

    /**
     * Stops gracefully: readiness turns to not-ready, new work is refused, work already started
     * gets up to the configured grace period to finish, then the server stops. Safe to call twice.
     */
    public void shutdown() {
        int running;
        synchronized (lifecycle) {
            if (state == State.DRAINING || state == State.STOPPED) {
                running = -1;
            } else {
                state = State.DRAINING;
                running = inFlight;
            }
        }
        if (running < 0) {
            try {
                stopped.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        log.log(LogLevel.INFO, "READINESS_CHANGED", "ready", "false", "state", State.DRAINING.name());
        log.log(LogLevel.INFO, "SHUTDOWN_STARTED", "inFlight", String.valueOf(running),
                "graceSeconds", String.valueOf(config.shutdownGraceSeconds()));
        int abandoned = drain();
        server.stop(0);
        executor.shutdownNow();
        synchronized (lifecycle) {
            state = State.STOPPED;
        }
        if (abandoned > 0) {
            log.log(LogLevel.WARN, "SHUTDOWN_TIMEOUT", "abandoned", String.valueOf(abandoned));
        }
        log.log(LogLevel.INFO, "SHUTDOWN_COMPLETED", "drained", String.valueOf(abandoned == 0));
        stopped.countDown();
    }

    /** Waits for started work to finish, up to the grace period. Returns how much was still running. */
    private int drain() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.shutdownGraceSeconds());
        synchronized (lifecycle) {
            while (inFlight > 0) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) {
                    break;
                }
                try {
                    lifecycle.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return inFlight;
        }
    }

    // ---- requests ----------------------------------------------------------------------------

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        // Work is counted from admission until its response has been written, so a graceful
        // shutdown waits for the caller to receive the answer, not only for the run to end.
        boolean tracked = path.equals("/assist") && method.equals("POST");
        boolean admitted = tracked && admit();
        try {
            respond(exchange, path, method, tracked && !admitted);
        } finally {
            if (admitted) {
                release();
            }
        }
    }

    private boolean admit() {
        synchronized (lifecycle) {
            if (state != State.READY) {
                return false;
            }
            inFlight++;
            return true;
        }
    }

    private void release() {
        synchronized (lifecycle) {
            inFlight--;
            lifecycle.notifyAll();
        }
    }

    private void respond(HttpExchange exchange, String path, String method, boolean refused) throws IOException {
        int status;
        Object body;
        String trace = "";
        try {
            switch (path) {
                case "/health" -> {
                    status = method.equals("GET") ? 200 : 405;
                    body = status == 200 ? Map.of("status", "UP") : error("METHOD_NOT_ALLOWED");
                }
                case "/ready" -> {
                    if (method.equals("GET")) {
                        State current = state();
                        boolean authorizationEnforced = AuthorizationSelfCheck.passes(authorizer);
                        boolean ready = current == State.READY && authorizationEnforced;
                        status = ready ? 200 : 503;
                        body = readinessBody(ready, current, authorizationEnforced);
                    } else {
                        status = 405;
                        body = error("METHOD_NOT_ALLOWED");
                    }
                }
                case "/info" -> {
                    status = method.equals("GET") ? 200 : 405;
                    body = status == 200 ? info() : error("METHOD_NOT_ALLOWED");
                }
                case "/assist" -> {
                    if (refused) {
                        status = 503;
                        body = error("NOT_READY");
                    } else if (method.equals("POST")) {
                        Reply reply = assist(exchange);
                        status = reply.status();
                        body = reply.body();
                        trace = reply.trace();
                    } else {
                        status = 405;
                        body = error("METHOD_NOT_ALLOWED");
                    }
                }
                default -> {
                    status = 404;
                    body = error("NOT_FOUND");
                }
            }
        } catch (RuntimeException e) {
            status = 500;
            body = error("INTERNAL_ERROR");
            log.log(LogLevel.ERROR, "REQUEST_FAILED", "path", knownPath(path), "error.type", e.getClass().getSimpleName());
        }
        log.log(path.equals("/health") || path.equals("/ready") ? LogLevel.DEBUG : LogLevel.INFO,
                path.equals("/health") ? "HEALTH_CHECK" : path.equals("/ready") ? "READINESS_CHECK" : "REQUEST_COMPLETED",
                "path", knownPath(path), "status", String.valueOf(status), "trace", trace);
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (status == 405) {
            exchange.getResponseHeaders().set("Allow", path.equals("/assist") ? "POST" : "GET");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Only the application's own paths are logged; a path an outsider made up is not copied into a log. */
    private static String knownPath(String path) {
        return Set.of("/health", "/ready", "/info", "/assist").contains(path) ? path : "(unknown)";
    }

    private static Map<String, Object> error(String code) {
        return Map.of("error", code);
    }

    private static Map<String, Object> readinessBody(boolean ready, State current, boolean authorizationEnforced) {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("lifecycle", current == State.READY ? "UP" : current.name());
        checks.put("authorization", authorizationEnforced ? "ENFORCED" : "FAILED");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "READY" : "NOT_READY");
        body.put("checks", checks);
        return body;
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", build.app());
        info.put("version", build.version());
        info.put("build", build.buildId());
        info.put("environment", config.environment());
        info.put("tools", List.copyOf(config.enabledTools()));
        return info;
    }

    private record Reply(int status, Object body, String trace) {
    }

    private Reply assist(HttpExchange exchange) throws IOException {
        Optional<Principal> principal = Optional.empty();
        String header = exchange.getRequestHeaders().getFirst("X-Principal");
        if (header != null) {
            if (!PRINCIPAL.matcher(header).matches()) {
                return new Reply(400, error("INVALID_PRINCIPAL"), "");
            }
            principal = Optional.of(new Principal(header));
        }
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (raw.length > MAX_BODY_BYTES) {
            return new Reply(413, error("REQUEST_TOO_LARGE"), "");
        }
        String request = new String(raw, StandardCharsets.UTF_8).strip();
        if (request.isEmpty()) {
            return new Reply(400, error("EMPTY_REQUEST"), "");
        }
        String traceId = String.format("run-%03d", runs.incrementAndGet());
        Assistant.Run run;
        synchronized (assistLock) {
            run = new Assistant(model, gateway).run(principal, request, new TraceRecorder(traceId, redactor));
        }
        recordRun(traceId, principal, run);
        return new Reply(200, runBody(traceId, principal, run), traceId);
    }

    private Map<String, Object> runBody(String traceId, Optional<Principal> principal, Assistant.Run run) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (GatewayResult result : run.results()) {
            results.add(Map.of("outcome", result.outcome().name(), "reason", redactor.redactSecrets(result.reason())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", traceId);
        body.put("principal", principal.map(Principal::id).orElse("none"));
        body.put("answer", redactor.redactSecrets(run.answer() == null ? "" : run.answer()));
        body.put("results", results);
        body.put("securityEvents", run.trace().events().stream().map(SecurityEvent::name).toList());
        return body;
    }

    /** One log line per security event, so authorization decisions are visible in the deployment's own log. */
    private void recordRun(String traceId, Optional<Principal> principal, Assistant.Run run) {
        Trace trace = run.trace();
        for (Span span : trace.ofType(SpanType.SECURITY_EVENT)) {
            SecurityEvent event = SecurityEvent.valueOf(span.name());
            log.log(levelOf(event), "SECURITY_EVENT", "trace", traceId, "securityEvent", event.name(),
                    "reason", span.attribute("reason").orElse(""), "tool", span.attribute("tool").orElse(""));
        }
        long executed = run.results().stream().filter(GatewayResult::executed).count();
        log.log(LogLevel.INFO, "RUN_COMPLETED", "trace", traceId, "principal", principal.map(Principal::id).orElse("none"),
                "toolCalls", String.valueOf(run.results().size()), "executed", String.valueOf(executed));
    }

    private static LogLevel levelOf(SecurityEvent event) {
        return switch (event) {
            case TOOL_EXECUTION_FAILED -> LogLevel.ERROR;
            case AUTHORIZATION_DENIED, ARGUMENT_VALIDATION_FAILED, APPROVAL_REQUIRED, APPROVAL_INVALIDATED -> LogLevel.WARN;
            default -> LogLevel.INFO;
        };
    }

    private static Set<String> knownSecrets(DeploymentConfig config) {
        Set<String> secrets = new java.util.HashSet<>();
        secrets.add(HelioPlatform.FAKE_API_TOKEN);
        config.monitoringToken().ifPresent(secrets::add);
        return secrets;
    }
}
