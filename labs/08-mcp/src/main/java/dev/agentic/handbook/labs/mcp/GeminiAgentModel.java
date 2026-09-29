package dev.agentic.handbook.labs.mcp;

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
 * The optional live implementation of {@link AgentModel}, backed by Gemini
 * through the official Google Gen AI Java SDK. Carried over from Lab 07.
 *
 * <p>The model is told about the two tools the application allows, in the
 * application's own words. It is not told which one is local and which one is
 * behind MCP, and it does not speak MCP: it proposes a function call, and the
 * runtime decides the rest. The declarations are deliberately not copied from
 * the MCP server's {@code tools/list} descriptions — that text comes from
 * another party, and forwarding it into the prompt is a trust decision of its
 * own (Milestone 11).
 *
 * <p>Stateful for one run: the instance accumulates the provider conversation.
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

    static final Tool TOOLS = Tool.builder()
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
        // The same structured function response whether the result came from
        // a local method or from an MCP server.
        history.add(Content.builder()
                .role("user")
                .parts(Part.fromFunctionResponse(exchange.request().name(), exchange.result()))
                .build());
        return callModel();
    }

    private ModelDecision callModel() {
        GenerateContentResponse response = client.models.generateContent(model, history, config);
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
     * FinalAnswer; anything else is rejected loudly instead of being guessed at.
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
}
