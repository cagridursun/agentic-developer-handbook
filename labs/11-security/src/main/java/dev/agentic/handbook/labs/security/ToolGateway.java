package dev.agentic.handbook.labs.security;

import dev.agentic.handbook.labs.security.AuthorizationDecision.Effect;
import dev.agentic.handbook.labs.security.GatewayResult.Outcome;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The execution boundary: the only path from a model proposal to a tool. A
 * proposal passes these stages in order, and any stage can stop it:
 *
 * <ol>
 *   <li>the tool is one the application registered (an allowlist);</li>
 *   <li>a principal is present (fail closed);</li>
 *   <li>argument validation (well formed? nothing unexpected?);</li>
 *   <li>the approval, if one is presented, covers exactly this operation;</li>
 *   <li>authorization (principal, capability, resource scope, operation type, approval state);</li>
 *   <li>a state-changing operation without an approval is held, and never runs;</li>
 *   <li>execution, then the result is cut down to the allowlisted fields and scrubbed.</li>
 * </ol>
 *
 * <p>Stages 3 to 5 run again, in full, when a held operation comes back with its
 * approval, so a change in policy or a changed argument between approval and
 * execution is caught. Nothing the model writes (the tool name excepted) is read
 * by a control: its justification is recorded and ignored.
 *
 * <p>Every stage records a {@link SecurityEvent}. Recording is not enforcing:
 * the denial happens in this class, whether or not anyone reads the trace.
 */
public final class ToolGateway {

    private final Map<String, ToolDefinition> tools;
    private final Authorizer authorizer;
    private final PendingOperations pending;
    private final Redactor redactor;

    public ToolGateway(Map<String, ToolDefinition> tools, Authorizer authorizer, PendingOperations pending,
            Redactor redactor) {
        this.tools = Map.copyOf(tools);
        this.authorizer = authorizer;
        this.pending = pending;
        this.redactor = redactor;
    }

    /** A proposal with no approval: a read runs if authorized; a state change is held for approval. */
    public GatewayResult invoke(RunContext context, ToolProposal proposal) {
        return handle(context, proposal, Optional.empty());
    }

    /** A proposal that comes back with the id of an approval the application holds. */
    public GatewayResult invokeApproved(RunContext context, ToolProposal proposal, String pendingOperationId) {
        return handle(context, proposal, Optional.of(pendingOperationId));
    }

    /**
     * An approver approves one held operation. The approver is authorized like anyone else, for the
     * target of that operation, and cannot be the requester.
     */
    public GatewayResult approve(RunContext approver, String pendingOperationId) {
        TraceRecorder trace = approver.trace();
        Span call = trace.begin(approver.parent(), SpanType.TOOL_CALL, "approve " + pendingOperationId);
        trace.put(call, "principal", approver.principal().map(Principal::id).orElse("none"));
        GatewayResult result = approveInternal(approver, pendingOperationId, call);
        trace.put(call, "outcome", result.outcome().name());
        trace.end(call, statusFor(result.outcome()));
        return result;
    }

    private GatewayResult approveInternal(RunContext approver, String id, Span call) {
        TraceRecorder trace = approver.trace();
        Optional<PendingOperations.Operation> operation = pending.find(id);
        if (operation.isEmpty() || operation.get().state() != PendingOperations.State.PENDING) {
            return deny(trace, call, "NOT_A_PENDING_OPERATION", "pending", id);
        }
        PendingOperations.Operation op = operation.get();
        AuthorizationDecision decision = decide(approver.principal(), Capability.APPROVE_OPERATIONS, op.target(),
                OperationType.GRANT_APPROVAL, ApprovalState.NOT_REQUESTED);
        if (decision.effect() != Effect.ALLOW) {
            return deny(trace, call, decision.reason(), "pending", id);
        }
        if (approver.principal().get().equals(op.requester())) {
            return deny(trace, call, "REQUESTER_CANNOT_APPROVE_OWN_OPERATION", "pending", id);
        }
        pending.approve(id, approver.principal().get());
        trace.event(call, SecurityEvent.APPROVAL_GRANTED, SpanStatus.OK, "pending", id,
                "approver", approver.principal().get().id(), "tool", op.tool(), "target", op.target(),
                "arguments", op.arguments());
        return new GatewayResult(Outcome.APPROVED, "APPROVED", Optional.of(id), Map.of());
    }

    private GatewayResult handle(RunContext context, ToolProposal proposal, Optional<String> pendingId) {
        TraceRecorder trace = context.trace();
        String label = proposal.tool() + "(" + redactor.describe(proposal.arguments()) + ")";
        Span call = trace.begin(context.parent(), SpanType.TOOL_CALL, label);
        trace.put(call, "principal", context.principal().map(Principal::id).orElse("none"));
        GatewayResult result = process(context, proposal, pendingId, call);
        trace.put(call, "outcome", result.outcome().name());
        trace.end(call, statusFor(result.outcome()));
        return result;
    }

    private GatewayResult process(RunContext context, ToolProposal proposal, Optional<String> pendingId, Span call) {
        TraceRecorder trace = context.trace();

        // 1. Allowlist: a tool the application did not register does not exist.
        ToolDefinition tool = proposal.tool() == null ? null : tools.get(proposal.tool());
        if (tool == null) {
            return deny(trace, call, "UNKNOWN_TOOL");
        }

        // 2. Identity: fail closed before doing any other work.
        if (context.principal().isEmpty()) {
            return deny(trace, call, "NO_PRINCIPAL");
        }
        Principal principal = context.principal().get();

        // 3. Validation: well formed, and nothing unexpected. Not authorization.
        ArgumentValidator.Result validation = ArgumentValidator.validate(tool, proposal.arguments());
        if (!validation.valid()) {
            trace.event(call, SecurityEvent.ARGUMENT_VALIDATION_FAILED, SpanStatus.REJECTED,
                    "violations", String.join("; ", validation.violations()));
            return GatewayResult.of(Outcome.INVALID_ARGUMENTS, String.join("; ", validation.violations()));
        }
        Map<String, Object> arguments = validation.arguments();
        String resource = (String) arguments.get(tool.resourceArgument());
        String canonical = PendingOperations.canonical(arguments);

        // 4. Approval: does the one presented cover exactly this operation?
        ApprovalState approval = ApprovalState.NOT_REQUESTED;
        if (pendingId.isPresent()) {
            Optional<String> mismatch = pending.mismatch(pendingId.get(), principal, tool.name(), resource, canonical);
            if (mismatch.isPresent()) {
                trace.event(call, SecurityEvent.APPROVAL_INVALIDATED, SpanStatus.REJECTED,
                        "pending", pendingId.get(), "reason", mismatch.get());
                return new GatewayResult(Outcome.APPROVAL_INVALID, mismatch.get(), pendingId, Map.of());
            }
            approval = ApprovalState.GRANTED;
        }

        // 5. Authorization.
        AuthorizationDecision decision = decide(context.principal(), tool.capability(), resource,
                tool.operationType(), approval);
        if (decision.effect() == Effect.DENY) {
            return deny(trace, call, decision.reason(), "capability", tool.capability().name(), "resource", resource);
        }

        // 6. A state change without an approval is held. It does not run.
        if (decision.effect() == Effect.REQUIRE_APPROVAL) {
            String id = pending.create(principal, tool.name(), resource, canonical);
            trace.event(call, SecurityEvent.APPROVAL_REQUIRED, SpanStatus.REJECTED, "pending", id,
                    "tool", tool.name(), "target", resource, "arguments", canonical);
            return new GatewayResult(Outcome.APPROVAL_REQUIRED, "STATE_CHANGE_NEEDS_APPROVAL", Optional.of(id), Map.of());
        }
        trace.event(call, SecurityEvent.AUTHORIZATION_ALLOWED, SpanStatus.OK, "capability", tool.capability().name(),
                "resource", resource, "operation_type", tool.operationType().name());

        // 7. Execution. An approval is single-use: it is spent as the operation starts.
        pendingId.ifPresent(pending::consume);
        trace.event(call, SecurityEvent.TOOL_EXECUTION_STARTED, SpanStatus.OK, "tool", tool.name());
        Map<String, Object> raw;
        try {
            raw = tool.executor().execute(principal, arguments);
        } catch (RuntimeException e) {
            String message = redactor.text(String.valueOf(e.getMessage()));
            trace.event(call, SecurityEvent.TOOL_EXECUTION_FAILED, SpanStatus.ERROR, "tool", tool.name(),
                    "error.type", e.getClass().getSimpleName(), "error.message", message);
            return GatewayResult.of(Outcome.FAILED, e.getClass().getSimpleName() + ": " + message);
        }
        Map<String, Object> result = filter(trace, call, tool, raw);
        trace.event(call, SecurityEvent.TOOL_EXECUTION_COMPLETED, SpanStatus.OK, "tool", tool.name(),
                "result", redactor.describe(result));
        return new GatewayResult(Outcome.EXECUTED, "EXECUTED", pendingId, result);
    }

    /** A failing authorizer is a denial: fail closed. */
    private AuthorizationDecision decide(Optional<Principal> principal, Capability capability, String resource,
            OperationType type, ApprovalState approval) {
        try {
            AuthorizationDecision decision = authorizer.authorize(
                    new AuthorizationRequest(principal, capability, resource, type, approval));
            return decision == null || decision.effect() == null
                    ? AuthorizationDecision.deny("NO_DECISION") : decision;
        } catch (RuntimeException e) {
            return AuthorizationDecision.deny("AUTHORIZER_FAILED");
        }
    }

    /**
     * Only allowlisted fields leave. A dropped field with a sensitive name is recorded as a redaction
     * (by name, never by value), and secret-shaped text inside an allowed field is scrubbed.
     */
    private Map<String, Object> filter(TraceRecorder trace, Span call, ToolDefinition tool, Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String field = entry.getKey();
            if (!tool.returnedFields().contains(field)) {
                if (redactor.isSensitiveKey(field)) {
                    trace.event(call, SecurityEvent.SENSITIVE_FIELD_REDACTED, SpanStatus.OK, "field", field,
                            "reason", "not_an_allowed_result_field");
                }
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String text) {
                String scrubbed = redactor.redactSecrets(text);
                if (!scrubbed.equals(text)) {
                    trace.event(call, SecurityEvent.SENSITIVE_FIELD_REDACTED, SpanStatus.OK, "field", field,
                            "reason", "secret_pattern_in_value");
                }
                value = scrubbed;
            }
            result.put(field, value);
        }
        return result;
    }

    private GatewayResult deny(TraceRecorder trace, Span call, String reason, String... fields) {
        String[] all = new String[fields.length + 2];
        all[0] = "reason";
        all[1] = reason;
        System.arraycopy(fields, 0, all, 2, fields.length);
        trace.event(call, SecurityEvent.AUTHORIZATION_DENIED, SpanStatus.REJECTED, all);
        return GatewayResult.of(Outcome.DENIED, reason);
    }

    private static SpanStatus statusFor(Outcome outcome) {
        return switch (outcome) {
            case EXECUTED, APPROVED -> SpanStatus.OK;
            case FAILED -> SpanStatus.ERROR;
            default -> SpanStatus.REJECTED;
        };
    }
}
