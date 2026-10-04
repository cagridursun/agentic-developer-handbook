package dev.agentic.handbook.labs.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What the gateway did with one proposal. Each stage that can stop a proposal has
 * its own outcome, so "it did not run" always says which control said no. There
 * is deliberately no single {@code safe} flag.
 */
public record GatewayResult(Outcome outcome, String reason, Optional<String> pendingOperationId,
        Map<String, Object> data) {

    public enum Outcome {
        EXECUTED,
        /** The tool ran and failed. */
        FAILED,
        /** Validation stopped it. Nothing ran. */
        INVALID_ARGUMENTS,
        /** Authorization stopped it. Nothing ran. */
        DENIED,
        /** Held until an approver approves exactly this operation. Nothing ran. */
        APPROVAL_REQUIRED,
        /** The approval presented does not cover this operation. Nothing ran. */
        APPROVAL_INVALID,
        /** An approval was recorded. Nothing ran yet. */
        APPROVED
    }

    public GatewayResult {
        // Insertion order is kept (Map.copyOf would reorder per JVM run), so output is repeatable.
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public boolean executed() {
        return outcome == Outcome.EXECUTED;
    }

    static GatewayResult of(Outcome outcome, String reason) {
        return new GatewayResult(outcome, reason, Optional.empty(), Map.of());
    }

    /** What is safe to hand back to the model: an outcome and a reason code, or the filtered data. */
    public String observation() {
        return outcome == Outcome.EXECUTED ? data.toString()
                : outcome + "(" + reason + ")" + pendingOperationId.map(id -> " " + id).orElse("");
    }
}
