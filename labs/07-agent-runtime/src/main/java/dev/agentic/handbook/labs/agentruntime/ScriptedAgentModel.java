package dev.agentic.handbook.labs.agentruntime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A deterministic test double, not an AI: it replays a fixed script of
 * decisions regardless of what it observes. It exists so the runtime's loop,
 * validation, and stopping rules can be demonstrated and tested with no
 * network, no key, and no nondeterminism.
 */
public final class ScriptedAgentModel implements AgentModel {

    private final Deque<ModelDecision> script;
    private final ModelDecision repeatForever;
    private int decisionsServed;

    private ScriptedAgentModel(Deque<ModelDecision> script, ModelDecision repeatForever) {
        this.script = script;
        this.repeatForever = repeatForever;
    }

    /** Replays the given decisions in order; fails if asked for more. */
    public static ScriptedAgentModel of(List<ModelDecision> decisions) {
        return new ScriptedAgentModel(new ArrayDeque<>(decisions), null);
    }

    /**
     * The model that never stops: it returns the same tool request on every
     * call. Used to prove that the runtime's step budget, not the model's
     * goodwill, ends the run.
     */
    public static ScriptedAgentModel repeating(ModelDecision.ToolRequest request) {
        return new ScriptedAgentModel(new ArrayDeque<>(), request);
    }

    /** How many decisions the runtime asked for. Lets tests prove the bound. */
    public int decisionsServed() {
        return decisionsServed;
    }

    @Override
    public ModelDecision start(String goal) {
        return nextDecision();
    }

    @Override
    public ModelDecision observe(ToolExchange exchange) {
        return nextDecision();
    }

    private ModelDecision nextDecision() {
        decisionsServed++;
        if (repeatForever != null) {
            return repeatForever;
        }
        ModelDecision next = script.pollFirst();
        if (next == null) {
            throw new IllegalStateException("The script is exhausted; the runtime asked for more decisions than scripted.");
        }
        return next;
    }
}
