package dev.agentic.handbook.labs.modelcall;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 01: the smallest useful Java-to-model interaction.
 *
 * <p>The application sends one prompt to a hosted Gemini model and prints the
 * response. There is no loop, no tools, no memory, and no retrieval.
 *
 * <p>This is an LLM call. It is not an agent.
 */
public final class ModelCall {

    /**
     * Default model for this lab. Google's model catalog recommends the
     * 3.8 Flash stable model for new projects. Override with GEMINI_MODEL.
     */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    private ModelCall() {
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();

        // 1. Configuration. The API key authenticates this application to the
        //    inference provider. The key is read, never printed.
        Optional<String> apiKey = apiKey(env);
        if (apiKey.isEmpty()) {
            System.err.println("No API key found.");
            System.err.println("Set the GOOGLE_API_KEY environment variable "
                    + "(create a key at https://aistudio.google.com/apikey).");
            System.exit(1);
        }
        String model = model(env);

        // 2. Input. One user message. This whole string is the prompt.
        String prompt = args.length > 0
                ? String.join(" ", args)
                : "In two sentences: what happens during one LLM inference call?";

        System.out.println("Model:  " + model);
        System.out.println("Prompt: " + prompt);
        System.out.println();

        // 3. The provider client. It sends HTTPS requests to the Gemini API.
        Client client = Client.builder().apiKey(apiKey.get()).build();

        // 4. Inference. The hosted model turns the prompt into a response.
        //    This single request/response exchange is the entire program.
        GenerateContentResponse response = client.models.generateContent(model, prompt, null);

        // 5. Output. The model's text, nothing else.
        System.out.println(response.text());
    }

    /**
     * Resolves the API key the way the official SDK documents it:
     * GOOGLE_API_KEY is current, GEMINI_API_KEY is legacy, and GOOGLE_API_KEY
     * wins when both are set. Blank values count as missing.
     */
    static Optional<String> apiKey(Map<String, String> env) {
        return firstNonBlank(env.get("GOOGLE_API_KEY"), env.get("GEMINI_API_KEY"));
    }

    /** Returns the model name from GEMINI_MODEL, or the lab default. */
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
