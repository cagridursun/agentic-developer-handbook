package dev.agentic.handbook.labs.agentruntime;

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
 * The live implementation of {@link AgentModel}, backed by Gemini through the
 * official Google Gen AI Java SDK.
 *
 * <p>All provider-specific types stay inside this class: the runtime never
 * sees Content, Part, or FunctionCall. Function declarations are attached so
 * the model may PROPOSE a call; the SDK's automatic function execution is
 * deliberately not used — every execution decision belongs to the runtime.
 *
 * <p>Stateful for one run: the instance accumulates the provider conversation
 * (goal, model function calls, function responses) exactly as the official
 * function-calling flow requires.
 */
public final class GeminiAgentModel implements AgentModel {

    private static final Schema SERVICE_NAME_PARAMETERS = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "serviceName", Schema.builder()
                            .type(Type.Known.STRING)
                            .description("The name of the platform service, for example \"notifications\".")
                            .build()))
            .required(List.of("serviceName"))
            .build();

    private static final Tool TOOLS = Tool.builder()
            .functionDeclarations(
                    FunctionDeclaration.builder()
                            .name("getServiceStatus")
                            .description("Returns the current operational status of a known platform service.")
                            .parameters(SERVICE_NAME_PARAMETERS)
                            .build(),
                    FunctionDeclaration.builder()
                            .name("getRecentDeployment")
                            .description("Returns the most recent deployment of a known platform service, if any.")
                            .parameters(SERVICE_NAME_PARAMETERS)
                            .build())
            .build();

    private final Client client;
    private final String model;
    private final GenerateContentConfig config;
    private final List<Content> history = new ArrayList<>();

    public GeminiAgentModel(String apiKey, String model) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = model;
        this.config = GenerateContentConfig.builder().tools(TOOLS).build();
    }

    @Override
    public ModelDecision start(String goal) {
        history.add(Content.builder().role("user").parts(Part.fromText(goal)).build());
        return callModel();
    }

    @Override
    public ModelDecision observe(ToolExchange exchange) {
        // The official flow: the tool result goes back as a structured
        // function response, not as prose.
        history.add(Content.builder()
                .role("user")
                .parts(Part.fromFunctionResponse(exchange.request().name(), exchange.result()))
                .build());
        return callModel();
    }

    private ModelDecision callModel() {
        GenerateContentResponse response = client.models.generateContent(model, history, config);
        // Keep the model's own turn (text or function call) in the history so
        // the next request carries the full exchange.
        response.candidates().ifPresent(candidates -> {
            if (!candidates.isEmpty()) {
                candidates.get(0).content().ifPresent(history::add);
            }
        });
        return toDecision(response);
    }

    /**
     * Translates one provider response into the runtime's decision language.
     * Exactly one function call becomes a ToolRequest; plain text becomes a
     * FinalAnswer; anything else — several calls in one turn, or neither text
     * nor call — is rejected loudly instead of being guessed at.
     */
    static ModelDecision toDecision(GenerateContentResponse response) {
        List<FunctionCall> calls = response.functionCalls() == null
                ? List.of()
                : List.copyOf(response.functionCalls());

        if (calls.size() > 1) {
            throw new IllegalStateException("The model requested " + calls.size()
                    + " tool calls in one turn. This runtime supports at most one tool call "
                    + "per model decision; parallel calls are a later runtime extension.");
        }
        if (calls.size() == 1) {
            FunctionCall call = calls.get(0);
            return new ModelDecision.ToolRequest(
                    call.name().orElse("(unnamed)"),
                    call.args().orElse(Map.of()));
        }

        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "The model returned neither a final answer nor a tool request.");
        }
        return new ModelDecision.FinalAnswer(text);
    }

    /** Convenience used by tests to build responses without a network. */
    static Optional<String> textOf(GenerateContentResponse response) {
        return Optional.ofNullable(response.text());
    }
}
