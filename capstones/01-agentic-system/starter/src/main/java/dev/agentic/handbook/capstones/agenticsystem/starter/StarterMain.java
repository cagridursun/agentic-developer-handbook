package dev.agentic.handbook.capstones.agenticsystem.starter;

import java.util.Map;

/**
 * The capstone starter. This program does not solve the incident — that is
 * the learner's work. It states the challenge, shows the available fictional
 * assets, and points at the decisions that must be made before coding.
 */
public final class StarterMain {

    static final String CHALLENGE = "The notifications service is slow after a release. "
            + "Investigate what is currently known and prepare a concise engineering handoff. "
            + "Do not claim a root cause without evidence.";

    private StarterMain() {
    }

    public static void main(String[] args) {
        System.out.println("Capstone 01 — Build a Small Agentic System");
        System.out.println();
        System.out.println("Challenge:");
        System.out.println(CHALLENGE);

        System.out.println();
        System.out.println("Available fictional assets:");
        System.out.println();
        System.out.println("Runbooks (a Knowledge / RAG decision):");
        for (Map.Entry<String, String> doc : StarterAssets.knowledge().entrySet()) {
            System.out.println("- knowledge/" + doc.getKey()
                    + " (" + doc.getValue().length() + " characters)");
        }
        System.out.println();
        System.out.println("Skill (a Skills decision):");
        System.out.println("- skills/incident-handoff/SKILL.md");
        System.out.println();
        System.out.println("Service fixtures (a Tools decision):");
        for (String service : ServiceFixtures.knownServices()) {
            System.out.println("- " + service + ": "
                    + ServiceFixtures.serviceStatus(service).orElseThrow().get("status"));
        }

        System.out.println();
        System.out.println("Before writing any more code: fill out DECISIONS.md.");
        System.out.println("Decide which capabilities this problem actually needs, and which");
        System.out.println("it deliberately does not. Then implement your design here.");

        // TODO(learner): decide your architecture in DECISIONS.md, then wire it
        // here. Nothing forces you toward the reference design: a fixed
        // workflow, a single model call over pre-fetched context, or a bounded
        // agent runtime can all be defensible, depending on your justification.
        // Constraints that always apply: read-only tools, bounded execution if
        // you build a loop, no API key needed for the deterministic path.
    }
}
