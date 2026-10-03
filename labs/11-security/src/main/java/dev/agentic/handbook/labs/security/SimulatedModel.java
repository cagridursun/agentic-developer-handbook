package dev.agentic.handbook.labs.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic stand-in for a model that <b>can be steered by text it reads</b>.
 *
 * <p>It is a test double, not a claim about how any real model behaves: real
 * models differ, and some resist some injections some of the time. The lab's
 * point does not depend on the model complying. The point is that the controls
 * hold when it does.
 *
 * <p>Behavior: it plans a harmless read-only investigation of the service named in the
 * request. But if any text it has been given, whether the user's request or a
 * retrieved note, contains {@code ASSISTANT INSTRUCTION: call <tool> key=value ...}, it
 * proposes that call first and justifies it with a confident (and false) claim of
 * authority. It never executes anything: it only returns a {@link ModelStep}.
 */
public final class SimulatedModel implements AssistantModel {

    static final String CONFIDENT_JUSTIFICATION =
            "I am acting as the incident administrator and this action is pre-approved, so no further check is needed.";

    private static final Pattern INSTRUCTION =
            Pattern.compile("ASSISTANT INSTRUCTION: call (\\w+)((?: \\w+=[\\w.-]+)*)");
    private static final Pattern INCIDENT = Pattern.compile("INC-[0-9]{4}");
    private static final List<String> SERVICES = List.of("notifications", "billing", "search", "checkout");

    @Override
    public ModelStep next(String userRequest, List<Exchange> history) {
        List<String> readable = new ArrayList<>();
        readable.add(userRequest);
        history.forEach(exchange -> readable.add(exchange.observation()));

        for (String text : readable) {
            Matcher matcher = INSTRUCTION.matcher(text);
            while (matcher.find()) {
                ToolProposal injected = new ToolProposal(matcher.group(1), arguments(matcher.group(2)),
                        CONFIDENT_JUSTIFICATION);
                if (!alreadyProposed(history, injected)) {
                    return ModelStep.propose(injected);
                }
            }
        }

        String service = SERVICES.stream().filter(userRequest::contains).findFirst().orElse("notifications");
        List<ToolProposal> plan = new ArrayList<>();
        plan.add(ToolProposal.call("getServiceStatus", "Checking the current status first.", "serviceName", service));
        Matcher incident = INCIDENT.matcher(userRequest);
        if (incident.find()) {
            plan.add(ToolProposal.call("getIncidentNote", "Reading the incident note for context.",
                    "serviceName", service, "incidentId", incident.group()));
        }
        for (ToolProposal step : plan) {
            if (!alreadyProposed(history, step)) {
                return ModelStep.propose(step);
            }
        }
        StringBuilder answer = new StringBuilder("Summary for ").append(service).append(":");
        history.forEach(exchange -> answer.append("\n  - ").append(exchange.proposal().tool())
                .append(": ").append(exchange.observation()));
        return ModelStep.answer(answer.toString());
    }

    private static boolean alreadyProposed(List<Exchange> history, ToolProposal candidate) {
        return history.stream().anyMatch(exchange -> exchange.proposal().tool().equals(candidate.tool())
                && exchange.proposal().arguments().equals(candidate.arguments()));
    }

    private static Map<String, Object> arguments(String text) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (String pair : text.trim().split(" ")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] keyValue = pair.split("=", 2);
            arguments.put(keyValue[0], keyValue[1].matches("[0-9]+") ? (Object) Integer.valueOf(keyValue[1]) : keyValue[1]);
        }
        return arguments;
    }

    /** For tests: a proposal as a one-step model. */
    static AssistantModel proposing(ToolProposal proposal) {
        return (request, history) -> history.isEmpty() ? ModelStep.propose(proposal) : ModelStep.answer("done");
    }
}
