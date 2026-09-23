package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A deterministic test double, not an AI: replays a fixed script (or one
 * decision forever) so the runtime is observable and testable without a key
 * or network.
 */
public final class ScriptedModel implements Agent.Model {

    private final Deque<Agent.Decision> script;
    private final Agent.Decision repeatForever;
    private int decisionsServed;

    private ScriptedModel(Deque<Agent.Decision> script, Agent.Decision repeatForever) {
        this.script = script;
        this.repeatForever = repeatForever;
    }

    public static ScriptedModel of(List<Agent.Decision> decisions) {
        return new ScriptedModel(new ArrayDeque<>(decisions), null);
    }

    public static ScriptedModel repeating(Agent.ToolRequest request) {
        return new ScriptedModel(new ArrayDeque<>(), request);
    }

    public int decisionsServed() {
        return decisionsServed;
    }

    @Override
    public Agent.Decision start(String initialContext) {
        return next();
    }

    @Override
    public Agent.Decision observe(Agent.ToolExchange exchange) {
        return next();
    }

    private Agent.Decision next() {
        decisionsServed++;
        if (repeatForever != null) {
            return repeatForever;
        }
        Agent.Decision decision = script.pollFirst();
        if (decision == null) {
            throw new IllegalStateException("Script exhausted.");
        }
        return decision;
    }
}
