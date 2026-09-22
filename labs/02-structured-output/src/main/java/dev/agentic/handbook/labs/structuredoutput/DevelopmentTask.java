package dev.agentic.handbook.labs.structuredoutput;

import java.util.ArrayList;
import java.util.List;

/**
 * The application's contract: what the rest of the code is allowed to rely on.
 *
 * <p>This record is the boundary between model output and application logic.
 * Code that consumes a {@code DevelopmentTask} never inspects model text.
 */
public record DevelopmentTask(
        String title,
        String summary,
        TaskType type,
        List<String> acceptanceCriteria) {

    /**
     * Business validation the schema cannot express. Schema-constrained output
     * guarantees structure, not sense: a blank title is valid JSON and valid
     * against the schema, and still useless to this application.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (title == null || title.isBlank()) {
            problems.add("title must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            problems.add("summary must not be blank");
        }
        if (type == null) {
            problems.add("type is missing");
        }
        if (acceptanceCriteria == null || acceptanceCriteria.isEmpty()) {
            problems.add("at least one acceptance criterion is required");
        }
        return problems;
    }
}
