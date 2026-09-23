package dev.agentic.handbook.labs.toolcalling;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lab 03: one manually controlled tool round.
 *
 * <p>The model does not execute the tool. Your application does. The model
 * proposes an action; this class validates the proposal, runs plain Java, and
 * sends the result back so the model can phrase the final answer.
 *
 * <p>This is a controlled tool-calling flow, not yet the Agent Runtime. There
 * is deliberately no loop here: exactly one tool round is allowed.
 */
public final class ToolCallingExample {

    /** Same default and override rules as the earlier labs. */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    /** The only tool name this application will execute. This is the allowlist. */
    static final String TOOL_NAME = "getServiceStatus";

    /**
     * The tool declaration: what the model is told exists. It is a name, a
     * description, and a parameter schema — nothing more. The model never sees
     * or touches the Java method behind it.
     *
     * <p>Declared manually on purpose. The SDK can also execute reflected Java
     * methods automatically, and this lab avoids that so the boundary between
     * model decision and application execution stays visible.
     */
    static final FunctionDeclaration GET_SERVICE_STATUS = FunctionDeclaration.builder()
            .name(TOOL_NAME)
            .description("Returns the current operational status of a known platform service.")
            .parameters(Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "serviceName", Schema.builder()
                                    .type(Type.Known.STRING)
                                    .description("The name of the service, for example \"authorization\".")
                                    .build()))
                    .required(List.of("serviceName"))
                    .build())
            .build();

    private ToolCallingExample() {
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

        String question = args.length > 0
                ? String.join(" ", args)
                : "What is the current status of the authorization service?";

        System.out.println("Model:    " + model);
        System.out.println("Question: " + question);
        System.out.println();

        // The declaration travels with the request. This is how the model learns
        // that a capability named getServiceStatus exists.
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(Tool.builder().functionDeclarations(GET_SERVICE_STATUS).build())
                .build();

        Client client = Client.builder().apiKey(apiKey.get()).build();

        // ---- First model call: the model decides whether it needs the tool. ----
        List<Content> conversation = new ArrayList<>();
        conversation.add(Content.builder().role("user").parts(Part.fromText(question)).build());

        GenerateContentResponse first = client.models.generateContent(model, conversation, config);

        List<FunctionCall> requests = first.functionCalls() == null
                ? List.of()
                : List.copyOf(first.functionCalls());

        if (requests.isEmpty()) {
            // The model answered from context alone. No capability was needed.
            System.out.println(first.text());
            return;
        }
        if (requests.size() > 1) {
            System.err.println("The model requested " + requests.size()
                    + " tool calls. This lab executes exactly one controlled round; "
                    + "handling several calls belongs to the Agent Runtime milestone.");
            System.exit(1);
        }

        FunctionCall request = requests.get(0);
        String requestedTool = request.name().orElse("(unnamed)");
        Map<String, Object> requestedArgs = request.args().orElse(Map.of());

        System.out.println("Tool call requested by the model:");
        System.out.println("  " + requestedTool + "(" + requestedArgs + ")");
        System.out.println();

        // ================= MODEL DECISION ENDS HERE. =================
        // Everything above was the model proposing. Everything below is the
        // application deciding, validating, and executing.
        // ================ APPLICATION EXECUTION BEGINS. ===============

        Map<String, Object> toolResult = handleToolRequest(requestedTool, requestedArgs);

        System.out.println("Tool result computed by this application:");
        System.out.println("  " + toolResult);
        System.out.println();

        // ---- Second model call: the model turns application data into an answer. ----
        // The conversation now contains the question, the model's tool request,
        // and the application's tool result.
        first.candidates().flatMap(c -> c.isEmpty()
                ? Optional.empty()
                : c.get(0).content()).ifPresent(conversation::add);
        conversation.add(Content.builder()
                .role("user")
                .parts(Part.fromFunctionResponse(TOOL_NAME, toolResult))
                .build());

        GenerateContentResponse second = client.models.generateContent(model, conversation, config);

        if (second.functionCalls() != null && !second.functionCalls().isEmpty()) {
            System.err.println("The model asked for another tool call after receiving the result. "
                    + "Multiple tool rounds are the Agent Runtime milestone, not this lab.");
            System.exit(1);
        }

        System.out.println("Final answer:");
        System.out.println(second.text());
    }

    /**
     * The trust boundary. The tool name and arguments were generated by a model,
     * so they are untrusted input: the name is checked against the allowlist and
     * the arguments are validated before any Java method runs.
     *
     * <p>The model proposes an action. The application authorizes and executes it.
     *
     * @return the structured tool result to send back to the model
     * @throws IllegalArgumentException when the request itself is malformed —
     *     an unlisted tool, missing or non-string arguments
     */
    static Map<String, Object> handleToolRequest(String toolName, Map<String, Object> arguments) {
        // 1. Only declared tools may run. Never resolve the name via reflection.
        if (!TOOL_NAME.equals(toolName)) {
            throw new IllegalArgumentException(
                    "Tool '" + toolName + "' is not on this application's allowlist.");
        }

        // 2. The arguments must be exactly what the declaration promised.
        if (arguments == null || !arguments.containsKey("serviceName")) {
            throw new IllegalArgumentException("Required argument 'serviceName' is missing.");
        }
        if (arguments.size() > 1) {
            throw new IllegalArgumentException(
                    "Unexpected arguments beyond 'serviceName': " + arguments.keySet());
        }
        if (!(arguments.get("serviceName") instanceof String serviceName) || serviceName.isBlank()) {
            throw new IllegalArgumentException("'serviceName' must be a non-blank string.");
        }

        // 3. Execute the capability: a normal Java method call.
        Optional<ServiceStatus> status = ServiceCatalog.getServiceStatus(serviceName);

        // 4. A well-formed request for an unknown service is not an application
        //    error. The result tells the model, and the model tells the user.
        if (status.isEmpty()) {
            return Map.of(
                    "error", "Unknown service: " + serviceName.trim(),
                    "knownServices", List.copyOf(ServiceCatalog.knownServices()));
        }

        return Map.of(
                "serviceName", status.get().serviceName(),
                "status", status.get().state().name(),
                "message", status.get().message());
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
