package dev.agentic.handbook.labs.skills;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 06: skills.
 *
 * <p>A scripted, deterministic demonstration: the same task performed with a
 * vague one-off prompt, with a procedure copied into the prompt (and a second
 * copy that drifted), and finally with the procedure extracted into a
 * version-controlled SKILL.md that the application selects and loads.
 *
 * <p>A skill is reusable procedure, not a capability. A SKILL.md file does
 * not make a program an agent — and this is still not an agent.
 */
public final class SkillsExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** Fictional, deterministic incident facts. No real system is described. */
    static final String INCIDENT_FACTS = """
            - Checkout latency p99 rose from 310 ms to 2.1 s starting 14:05 UTC.
            - Alert LATENCY-CHECKOUT-P99 fired at 14:07 UTC.
            - Deploy of checkout service v2026.09.3 completed at 13:58 UTC.
            - Rollback to v2026.09.2 finished at 14:31 UTC; p99 back to 320 ms by 14:36 UTC.
            - Root cause is not proven yet.
            - Open follow-up: compare connection-pool metrics between the two versions (owner: tonight's on-call engineer).""";

    /** The full incident-handoff procedure, as teams write it into prompts. */
    static final List<String> PROCEDURE_A = List.of(
            "Separate observed facts from hypotheses.",
            "State the customer impact.",
            "Cite the evidence provided.",
            "List unresolved questions.",
            "Give next actions and owners.",
            "Do not invent a root cause.");

    /**
     * A copied version of the same procedure after typical drift: one rule
     * silently disappeared during copy-paste between prompts.
     */
    static final List<String> PROCEDURE_B = List.of(
            "State the customer impact.",
            "Cite the evidence provided.",
            "List unresolved questions.",
            "Give next actions and owners.",
            "Do not invent a root cause.");

    private SkillsExample() {
    }

    public static void main(String[] args) {
        banner("1. ONE-OFF PROMPT");
        String vaguePrompt = buildVaguePrompt(INCIDENT_FACTS);
        System.out.println(vaguePrompt);
        System.out.println("(" + vaguePrompt.length() + " characters, procedure supplied: none)");
        System.out.println();
        System.out.println("The task can be performed, but the application specified no");
        System.out.println("procedure. The model must invent the structure, the fact/hypothesis");
        System.out.println("boundary, and the standards for what not to make up.");

        banner("2. NAIVE FIX: COPY THE PROCEDURE INTO EVERY PROMPT");
        System.out.println("Procedure copy A rules: " + PROCEDURE_A.size());
        System.out.println("Procedure copy B rules: " + PROCEDURE_B.size());
        for (String rule : missingRules(PROCEDURE_A, PROCEDURE_B)) {
            System.out.println("Missing from B:         " + rule);
        }
        System.out.println();
        System.out.println("Both prompts intend the same task, but they now encode different");
        System.out.println("procedures. This is not a token-count problem: it is duplication,");
        System.out.println("inconsistency, and drift. Improving the procedure means finding and");
        System.out.println("fixing every copy.");

        banner("3. SKILL");
        // Progressive disclosure, stage 1: metadata only. This is what a
        // selection step reads - nobody pays for instruction bodies yet.
        System.out.println("Available skill metadata:");
        for (String name : SkillLoader.AVAILABLE_SKILLS) {
            SkillMetadata metadata = SkillLoader.metadata(name);
            System.out.println("- " + metadata.name() + ": "
                    + metadata.description().substring(0, Math.min(60, metadata.description().length()))
                    + "...");
        }
        System.out.println();

        // THE APPLICATION selects the skill, explicitly and deterministically.
        // Model-driven skill selection belongs to the Agent Runtime milestone.
        System.out.println("Selected: incident-handoff");
        Skill skill = SkillLoader.load("incident-handoff");
        System.out.println("Instructions loaded from: skills/incident-handoff/SKILL.md");
        System.out.println("(release-summary metadata was listed; its body was never loaded)");
        System.out.println();

        String skillPrompt = buildSkillPrompt(skill, INCIDENT_FACTS);
        System.out.println("Final prompt: " + skillPrompt.length()
                + " characters, procedure loaded from one canonical, reviewable file.");

        // The single, optional real model call of this lab.
        Optional<String> apiKey = apiKey(System.getenv());
        if (apiKey.isPresent()) {
            String model = model(System.getenv());
            System.out.println();
            System.out.println("Model: " + model);
            Client client = Client.builder().apiKey(apiKey.get()).build();
            GenerateContentResponse response =
                    client.models.generateContent(model, skillPrompt, null);
            System.out.println();
            System.out.println(response.text());
        } else {
            System.out.println();
            System.out.println("GOOGLE_API_KEY is not set, so the real generation step is skipped.");
            System.out.println("The demonstration above is complete without it.");
        }
    }

    /** Experience 1: the facts and a vague task. No procedure. */
    static String buildVaguePrompt(String facts) {
        return "Write an incident handoff from these facts.\n\nFacts:\n" + facts + "\n";
    }

    /** The naive fix: the procedure travels inside every individual prompt. */
    static String buildProcedurePrompt(List<String> procedure, String facts) {
        StringBuilder prompt = new StringBuilder("When preparing an incident handoff:\n\n");
        for (int i = 0; i < procedure.size(); i++) {
            prompt.append(i + 1).append(". ").append(procedure.get(i)).append("\n");
        }
        prompt.append("\nFacts:\n").append(facts).append("\n");
        return prompt.toString();
    }

    /**
     * The skill-backed prompt: the application loaded one canonical procedure
     * and placed it into context. The tags are structure, not a security
     * boundary — skill text is untrusted input, and no skill instruction can
     * grant a capability or override application policy.
     */
    static String buildSkillPrompt(Skill skill, String facts) {
        return "You are performing an engineering task.\n\n"
                + "The application selected the following reusable procedure:\n\n"
                + "<skill name=\"" + skill.name() + "\">\n"
                + skill.instructions()
                + "\n</skill>\n\n"
                + "Task input:\n" + facts + "\n\n"
                + "Follow the skill when producing the result. Do not invent missing facts.\n";
    }

    /** Deterministic drift evidence: which rules of A are absent from B. */
    static List<String> missingRules(List<String> original, List<String> copy) {
        List<String> missing = new ArrayList<>(original);
        missing.removeAll(copy);
        return List.copyOf(missing);
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println(title);
        System.out.println("--------------------------------------------------");
    }

    /** GOOGLE_API_KEY first, legacy GEMINI_API_KEY second — the SDK's documented precedence. */
    static Optional<String> apiKey(Map<String, String> env) {
        return firstNonBlank(env.get("GOOGLE_API_KEY"), env.get("GEMINI_API_KEY"));
    }

    /** Model name from GEMINI_MODEL, or the lab default. */
    static String model(Map<String, String> env) {
        return firstNonBlank(env.get("GEMINI_MODEL")).orElse(DEFAULT_MODEL);
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
