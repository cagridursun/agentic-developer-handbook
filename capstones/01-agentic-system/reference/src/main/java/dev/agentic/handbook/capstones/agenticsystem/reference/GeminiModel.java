package dev.agentic.handbook.capstones.agenticsystem.reference;

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

/**
 * The live implementation of {@link Agent.Model}, Lab 07 style: provider
 * types stay inside this class, function declarations let Gemini PROPOSE a
 * call, the SDK's automatic function execution is not used, and tool results
 * go back as structured function responses. Stateful for one run.
 */
public final class GeminiModel implements Agent.Model {

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

    public GeminiModel(String apiKey, String model) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = model;
        this.config = GenerateContentConfig.builder().tools(TOOLS).build();
    }

    @Override
    public Agent.Decision start(String initialContext) {
        history.add(Content.builder().role("user").parts(Part.fromText(initialContext)).build());
        return callModel();
    }

    @Override
    public Agent.Decision observe(Agent.ToolExchange exchange) {
        history.add(Content.builder()
                .role("user")
                .parts(Part.fromFunctionResponse(exchange.request().name(), exchange.result()))
                .build());
        return callModel();
    }

    private Agent.Decision callModel() {
        GenerateContentResponse response = client.models.generateContent(model, history, config);
        response.candidates().ifPresent(candidates -> {
            if (!candidates.isEmpty()) {
                candidates.get(0).content().ifPresent(history::add);
            }
        });
        return toDecision(response);
    }

    static Agent.Decision toDecision(GenerateContentResponse response) {
        List<FunctionCall> calls = response.functionCalls() == null
                ? List.of()
                : List.copyOf(response.functionCalls());
        if (calls.size() > 1) {
            throw new IllegalStateException("The model requested " + calls.size()
                    + " tool calls in one turn; this runtime supports at most one per decision.");
        }
        if (calls.size() == 1) {
            FunctionCall call = calls.get(0);
            return new Agent.ToolRequest(call.name().orElse("(unnamed)"), call.args().orElse(Map.of()));
        }
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "The model returned neither a final answer nor a tool request.");
        }
        return new Agent.FinalAnswer(text);
    }
}
