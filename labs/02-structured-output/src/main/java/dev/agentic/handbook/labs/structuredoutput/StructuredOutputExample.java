package dev.agentic.handbook.labs.structuredoutput;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 02: schema-constrained model output, deserialized into a Java record.
 *
 * <p>Lab 01 returned a String. Here the model is constrained by a response
 * schema, and the application receives a {@link DevelopmentTask}.
 *
 * <p>This is structured model output. It is still not an agent.
 */
public final class StructuredOutputExample {

    /** Same default and override rules as Lab 01. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /**
     * The response schema. This is not part of the prompt text: it is sent as a
     * generation constraint, and the API decodes output against it. Field
     * descriptions guide the model; {@code required} rejects omissions; the
     * enum format restricts {@code type} to the names of {@link TaskType}.
     */
    static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "title", Schema.builder()
                            .type(Type.Known.STRING)
                            .description("Short imperative name for the development task.")
                            .build(),
                    "summary", Schema.builder()
                            .type(Type.Known.STRING)
                            .description("One or two sentences describing what must be built and why.")
                            .build(),
                    "type", Schema.builder()
                            .type(Type.Known.STRING)
                            .format("enum")
                            .enum_(List.of("FEATURE", "BUG", "TECHNICAL_TASK"))
                            .description("The kind of work item.")
                            .build(),
                    "acceptanceCriteria", Schema.builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder()
                                    .type(Type.Known.STRING)
                                    .description("One verifiable acceptance criterion.")
                                    .build())
                            .description("Concrete conditions that make the task done.")
                            .build()))
            .required(List.of("title", "summary", "type", "acceptanceCriteria"))
            .build();

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private StructuredOutputExample() {
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();

        Optional<String> apiKey = apiKey(env);
        if (apiKey.isEmpty()) {
            System.err.println("No API key found.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }
        String model = model(env);

        // 1. Input: an informal feature request, exactly as a human would write it.
        String request = args.length > 0
                ? String.join(" ", args)
                : "Users should be able to reset their password from the login page. "
                        + "Send them a temporary link by email and make it expire after 15 minutes.";

        System.out.println("Model:   " + model);
        System.out.println("Request: " + request);
        System.out.println();

        // 2. The generation constraint: respond as JSON, decoded against the schema.
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        Client client = Client.builder().apiKey(apiKey.get()).build();

        String prompt = "Convert this informal feature request into a development task:\n" + request;

        // 3. Inference, same as Lab 01 — but the response is now constrained.
        GenerateContentResponse response = client.models.generateContent(model, prompt, config);

        // 4. The type boundary: model JSON becomes a Java record. From here on,
        //    application code works with DevelopmentTask, never with model text.
        DevelopmentTask task = parse(response.text());

        System.out.println("Title:    " + task.title());
        System.out.println("Type:     " + task.type());
        System.out.println("Summary:  " + task.summary());
        System.out.println("Acceptance criteria:");
        task.acceptanceCriteria().forEach(criterion -> System.out.println("  - " + criterion));

        // 5. Application validation. The schema guaranteed the shape; only the
        //    application can decide whether the values are acceptable.
        List<String> problems = task.problems();
        if (!problems.isEmpty()) {
            System.out.println();
            System.out.println("Rejected by application validation:");
            problems.forEach(problem -> System.out.println("  - " + problem));
            System.exit(1);
        }
    }

    /** Deserializes the model's JSON into the record, failing loudly on any mismatch. */
    static DevelopmentTask parse(String json) {
        try {
            return JSON.readValue(json, DevelopmentTask.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Model response does not match the DevelopmentTask contract: " + e.getMessage(), e);
        }
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
