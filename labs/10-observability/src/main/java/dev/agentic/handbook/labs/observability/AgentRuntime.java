package dev.agentic.handbook.labs.observability;

import dev.agentic.handbook.labs.observability.StructuredLog.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Lab 09 bounded runtime with <em>instrumentation</em> added. The loop, the
 * step budget, and the allowlist are unchanged; the new code records what
 * happens and never changes what happens. Take the {@code rec.*}, {@code metrics.*}
 * and logging lines away and it is the Lab 09 runtime.
 *
 * <p>Two things differ from Lab 09, and both exist so the trace can be honest:
 * <ul>
 *   <li>Validation and execution are separate steps. Lab 09 did both inside one
 *       method; here the boundary between "the application decides this may
 *       run" and "the tool runs" is where two spans meet. Rejections behave
 *       exactly as before.</li>
 *   <li>A model or tool exception stops the run with {@link StopReason#ERROR}
 *       instead of escaping, so the trace of a failed run is complete.</li>
 * </ul>
 *
 * <p>Educational duplication: this lab does not import Lab 09.
 */
public final class AgentRuntime {

    /** The authorization boundary: only these tools run. */
    private static final Set<String> ALLOWLIST = Set.of("getServiceStatus", "getRecentDeployment");

    private final AgentModel model;
    private final int maxSteps;
    private final Telemetry telemetry;
    private final ToolBackend tools;

    public AgentRuntime(AgentModel model, int maxSteps, Telemetry telemetry) {
        this(model, maxSteps, telemetry, ToolBackend.helio());
    }

    public AgentRuntime(AgentModel model, int maxSteps, Telemetry telemetry, ToolBackend tools) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }
        this.model = model;
        this.maxSteps = maxSteps;
        this.telemetry = telemetry;
        this.tools = tools;
    }

    /** Runs the loop for one goal, always terminating within maxSteps model decisions. */
    public ObservedRun run(String goal) {
        Redactor redactor = telemetry.redactor();
        Metrics metrics = telemetry.metrics();
        TraceRecorder rec = telemetry.newRecorder();

        Span root = rec.begin(null, SpanType.AGENT_RUN, "agent run");
        rec.put(root, "goal", goal);
        rec.put(root, "max_steps", String.valueOf(maxSteps));
        metrics.increment(Metrics.RUNS);

        List<ToolExchange> exchanges = new ArrayList<>();
        ToolExchange lastExchange = null;

        for (int step = 1; step <= maxSteps; step++) {
            // MODEL: one call. The model proposes; nothing has happened yet.
            Span modelCall = rec.begin(root, SpanType.MODEL_CALL, "model call " + step);
            AgentModel.ModelInfo info = model.info();
            rec.put(modelCall, "model.provider", info.provider());
            rec.put(modelCall, "model.name", info.name());
            metrics.increment(Metrics.STEPS);

            ModelDecision decision;
            try {
                decision = lastExchange == null ? model.start(goal) : model.observe(lastExchange);
            } catch (RuntimeException failure) {
                rec.endWithError(modelCall, SpanStatus.ERROR, failure.getClass().getSimpleName(), failure.getMessage());
                rec.log(Level.ERROR, modelCall, "model_error", "error.type", failure.getClass().getSimpleName(),
                        "error.message", String.valueOf(failure.getMessage()));
                return error(rec, root, step, exchanges, Optional.empty(), failure);
            }
            model.lastUsage().ifPresent(usage -> {
                usage.input().ifPresent(n -> rec.put(modelCall, "input_tokens", String.valueOf(n)));
                usage.output().ifPresent(n -> rec.put(modelCall, "output_tokens", String.valueOf(n)));
                usage.total().ifPresent(n -> rec.put(modelCall, "total_tokens", String.valueOf(n)));
            });

            boolean answer = decision instanceof ModelDecision.FinalAnswer;
            Span proposal = rec.begin(modelCall, SpanType.AGENT_DECISION, switch (decision) {
                case ModelDecision.FinalAnswer a -> "propose a final answer";
                case ModelDecision.ToolRequest r -> "propose " + r.name() + "(" + redactor.describe(r.arguments()) + ")";
            });
            rec.put(proposal, "decision", answer ? "final_answer" : "tool_request");
            rec.end(proposal, SpanStatus.OK);
            rec.put(modelCall, "outcome", answer ? "final_answer" : "tool_request");
            rec.end(modelCall, SpanStatus.OK);

            if (decision instanceof ModelDecision.FinalAnswer finalAnswer) {
                Span response = rec.begin(root, SpanType.FINAL_RESPONSE, "final response");
                rec.put(response, "answer", finalAnswer.text());
                rec.end(response, SpanStatus.OK);
                return finish(rec, root, StopReason.FINAL_ANSWER, SpanStatus.OK, step, exchanges,
                        Optional.of(finalAnswer.text()), Optional.empty(), Optional.empty());
            }

            ModelDecision.ToolRequest request = (ModelDecision.ToolRequest) decision;
            String arguments = redactor.describe(request.arguments());
            Span call = rec.begin(root, SpanType.TOOL_CALL, request.name() + "(" + arguments + ")");
            rec.put(call, "tool", request.name());
            rec.put(call, "arguments", arguments);
            rec.put(call, "proposal", proposal.spanId());
            metrics.increment(Metrics.TOOL_CALLS);

            // APPLICATION: the proposal becomes executable only if it passes this.
            Span validation = rec.begin(call, SpanType.TOOL_VALIDATION, "allowlist and arguments");
            String serviceName;
            try {
                serviceName = validate(request);
            } catch (IllegalArgumentException rejected) {
                rec.endWithError(validation, SpanStatus.REJECTED, rejected.getClass().getSimpleName(),
                        rejected.getMessage());
                rec.end(call, SpanStatus.REJECTED);
                metrics.increment(Metrics.TOOL_REJECTED);
                rec.log(Level.WARN, call, "tool_rejected", "tool", request.name(), "arguments", arguments,
                        "error.message", rejected.getMessage());
                return finish(rec, root, StopReason.REJECTED_TOOL_CALL, SpanStatus.REJECTED, step, exchanges,
                        Optional.empty(),
                        Optional.of(new AgentRunResult.Failure(request, redactor.text(rejected.getMessage()))),
                        Optional.empty());
            }
            rec.put(validation, "outcome", "allowed");
            rec.end(validation, SpanStatus.OK);

            // TOOL: the application's code runs it.
            Span execution = rec.begin(call, SpanType.TOOL_EXECUTION, request.name());
            Map<String, Object> result;
            try {
                result = tools.execute(request.name(), serviceName);
            } catch (RuntimeException failure) {
                rec.endWithError(execution, SpanStatus.ERROR, failure.getClass().getSimpleName(),
                        failure.getMessage());
                rec.end(call, SpanStatus.ERROR);
                metrics.increment(Metrics.TOOL_ERRORS);
                rec.log(Level.ERROR, call, "tool_error", "tool", request.name(),
                        "error.type", failure.getClass().getSimpleName(),
                        "error.message", String.valueOf(failure.getMessage()));
                return error(rec, root, step, exchanges, Optional.of(request), failure);
            }
            rec.put(execution, "result", redactor.describe(result));
            rec.end(execution, SpanStatus.OK);
            rec.end(call, SpanStatus.OK);
            rec.log(Level.INFO, call, "tool_call", "tool", request.name(), "status", "OK",
                    "duration_ms", String.valueOf(call.duration().toMillis()));

            lastExchange = new ToolExchange(request, result);
            exchanges.add(lastExchange);
        }

        rec.put(root, "error.type", "StepBudgetExhausted");
        rec.put(root, "error.message", "The budget of " + maxSteps
                + " model decisions was used without a final answer.");
        return finish(rec, root, StopReason.MAX_STEPS, SpanStatus.ERROR, maxSteps, exchanges,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private ObservedRun error(TraceRecorder rec, Span root, int step, List<ToolExchange> exchanges,
            Optional<ModelDecision.ToolRequest> request, RuntimeException failure) {
        Redactor redactor = telemetry.redactor();
        String message = redactor.text(failure.getMessage());
        rec.put(root, "error.type", failure.getClass().getSimpleName());
        rec.put(root, "error.message", String.valueOf(failure.getMessage()));
        return finish(rec, root, StopReason.ERROR, SpanStatus.ERROR, step, exchanges, Optional.empty(),
                request.map(r -> new AgentRunResult.Failure(r, message)),
                Optional.of(failure.getClass().getSimpleName() + ": " + message));
    }

    private ObservedRun finish(TraceRecorder rec, Span root, StopReason reason, SpanStatus status, int steps,
            List<ToolExchange> exchanges, Optional<String> answer, Optional<AgentRunResult.Failure> failure,
            Optional<String> error) {
        rec.put(root, "stop_reason", reason.name());
        rec.put(root, "steps", String.valueOf(steps));
        rec.end(root, status);

        Metrics metrics = telemetry.metrics();
        long millis = root.duration().toMillis();
        metrics.add(Metrics.RUN_DURATION_MS, millis);
        if (status != SpanStatus.OK) {
            metrics.increment(Metrics.FAILURES);
        }
        Level level = switch (status) {
            case OK -> Level.INFO;
            case REJECTED -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
        rec.log(level, root, "run_end", "stop_reason", reason.name(), "steps", String.valueOf(steps),
                "duration_ms", String.valueOf(millis));

        AgentRunResult result = new AgentRunResult(reason, answer, steps, List.copyOf(exchanges), failure, error);
        return new ObservedRun(result, rec.trace());
    }

    /** Argument and allowlist checks, in the Lab 09 order. Returns the service name to execute with. */
    private static String validate(ModelDecision.ToolRequest request) {
        String serviceName = requiredServiceName(request.arguments());
        if (!ALLOWLIST.contains(request.name())) {
            throw new IllegalArgumentException(
                    "Tool '" + request.name() + "' is not on this application's allowlist.");
        }
        String normalized = serviceName.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ServiceTools.knownServices().contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown service '" + normalized + "'. Known services: " + ServiceTools.knownServices());
        }
        return serviceName;
    }

    private static String requiredServiceName(Map<String, Object> arguments) {
        if (arguments == null || !arguments.containsKey("serviceName")) {
            throw new IllegalArgumentException("Required argument 'serviceName' is missing.");
        }
        if (arguments.size() > 1) {
            throw new IllegalArgumentException(
                    "Unexpected arguments beyond 'serviceName': " + arguments.keySet());
        }
        if (!(arguments.get("serviceName") instanceof String serviceName) || serviceName.isBlank()) {
            throw new IllegalArgumentException("'serviceName' must be a non-blank string.");
        }
        return serviceName;
    }
}
