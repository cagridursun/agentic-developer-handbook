package dev.agentic.handbook.labs.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wiring for the scenario: one simulated platform, one redactor that knows the
 * fake token, one approval record, and the gateway over them. It is plain
 * construction, not a framework; the demo and the tests build it the same way.
 */
public final class World {

    public static final Principal TRIAGE_ASSISTANT = new Principal("triage-assistant");
    public static final Principal INCIDENT_RESPONDER = new Principal("incident-responder");
    public static final Principal INCIDENT_COMMANDER = new Principal("incident-commander");

    /** One run's recorder with its root span already open, so direct gateway calls have a parent. */
    public final class Session {
        private final TraceRecorder recorder = new TraceRecorder(String.format("run-%03d", ++runs), redactor);
        private final Span root = recorder.begin(null, SpanType.AGENT_RUN, "session");
        private final Optional<Principal> principal;

        private Session(Optional<Principal> principal) {
            this.principal = principal;
            recorder.put(root, "principal", principal.map(Principal::id).orElse("none"));
        }

        public RunContext context() {
            return new RunContext(principal, recorder, root);
        }

        /** Ends the root span and returns everything recorded. */
        public Trace trace() {
            recorder.end(root, SpanStatus.OK);
            return recorder.trace();
        }
    }

    public final HelioPlatform platform;
    public final Redactor redactor = new Redactor(Set.of(HelioPlatform.FAKE_API_TOKEN));
    public final PendingOperations pending = new PendingOperations();
    public final ToolGateway gateway;
    private int runs;

    private World(Authorizer authorizer, HelioPlatform platform, Map<String, ToolDefinition> extraTools) {
        this.platform = platform;
        Map<String, ToolDefinition> tools = new LinkedHashMap<>(HelioTools.definitions(platform));
        tools.putAll(extraTools);
        this.gateway = new ToolGateway(tools, authorizer, pending, redactor);
    }

    /** The Helio tools and the Helio policy. */
    public static World standard() {
        return new World(PolicyAuthorizer.helioPolicy(), new HelioPlatform(), Map.of());
    }

    /** The Helio tools over a given platform, with a different authorizer, and optionally more tools. */
    public static World with(Authorizer authorizer, HelioPlatform platform, Map<String, ToolDefinition> extraTools) {
        return new World(authorizer, platform, extraTools);
    }

    public Session session(Principal principal) {
        return new Session(Optional.ofNullable(principal));
    }

    public Assistant assistant(AssistantModel model) {
        return new Assistant(model, gateway);
    }

    /** Runs the assistant as a principal and returns its result. */
    public Assistant.Run runAssistant(AssistantModel model, Principal principal, String request) {
        runs++;
        return assistant(model).run(Optional.ofNullable(principal), request,
                new TraceRecorder(String.format("run-%03d", runs), redactor));
    }
}
