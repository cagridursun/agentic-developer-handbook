package dev.agentic.handbook.labs.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The application's record of operations held for approval, and of the approvals
 * given. An approval is bound to one operation: the tool, the target, the exact
 * arguments, the requester, the approver, and the pending-operation id. It is
 * checked against the operation about to run, and any difference invalidates it
 * for good. It is single-use. It lives in memory, never expires, and is not
 * signed: this is a teaching record, not an approval platform.
 */
public final class PendingOperations {

    public enum State { PENDING, APPROVED, INVALIDATED, CONSUMED }

    public record Operation(String id, Principal requester, String tool, String target, String arguments,
            State state, Optional<Principal> approver) {
    }

    private final Map<String, Operation> operations = new LinkedHashMap<>();

    /** Holds an operation. {@code arguments} is the canonical form from {@link #canonical}. */
    String create(Principal requester, String tool, String target, String arguments) {
        String id = String.format("pending-%03d", operations.size() + 1);
        operations.put(id, new Operation(id, requester, tool, target, arguments, State.PENDING, Optional.empty()));
        return id;
    }

    public Optional<Operation> find(String id) {
        return Optional.ofNullable(operations.get(id));
    }

    void approve(String id, Principal approver) {
        Operation op = operations.get(id);
        operations.put(id, new Operation(op.id(), op.requester(), op.tool(), op.target(), op.arguments(),
                State.APPROVED, Optional.of(approver)));
    }

    void consume(String id) {
        setState(id, State.CONSUMED);
    }

    /**
     * Whether the approval {@code id} covers exactly this operation. Empty if it does. Any difference in
     * requester, tool, target, or arguments invalidates the approval permanently and returns the reason.
     */
    Optional<String> mismatch(String id, Principal requester, String tool, String target, String arguments) {
        Operation op = operations.get(id);
        if (op == null) {
            return Optional.of("UNKNOWN_PENDING_OPERATION");
        }
        if (op.state() == State.CONSUMED) {
            return Optional.of("APPROVAL_ALREADY_USED");
        }
        if (op.state() == State.INVALIDATED) {
            return Optional.of("APPROVAL_INVALIDATED");
        }
        String difference = !op.requester().equals(requester) ? "requester"
                : !op.tool().equals(tool) ? "operation"
                : !op.target().equals(target) ? "target"
                : !op.arguments().equals(arguments) ? "arguments" : null;
        if (difference != null) {
            setState(id, State.INVALIDATED);
            return Optional.of("APPROVAL_DOES_NOT_MATCH_" + difference.toUpperCase(java.util.Locale.ROOT));
        }
        if (op.state() != State.APPROVED) {
            return Optional.of("NOT_YET_APPROVED");
        }
        return Optional.empty();
    }

    private void setState(String id, State state) {
        Operation op = operations.get(id);
        operations.put(id, new Operation(op.id(), op.requester(), op.tool(), op.target(), op.arguments(),
                state, op.approver()));
    }

    /** A stable text form of validated arguments, so "the same arguments" has one meaning. */
    static String canonical(Map<String, Object> arguments) {
        StringBuilder text = new StringBuilder();
        new java.util.TreeMap<>(arguments).forEach((key, value) -> {
            if (!text.isEmpty()) {
                text.append('&');
            }
            text.append(key).append('=').append(value);
        });
        return text.toString();
    }
}
