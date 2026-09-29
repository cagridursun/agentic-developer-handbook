package dev.agentic.handbook.labs.mcp.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * The smallest useful MCP server: the (fictional) deployment team's process,
 * exposing exactly one tool, {@code getRecentDeployment}, over STDIO.
 *
 * <p>It is not an agent. It has no model, no loop, and no goals: it answers
 * {@code tools/list} and {@code tools/call} requests about data it owns, and
 * nothing else — no resources, no prompts, no sampling.
 *
 * <p>STDIO rule: standard output carries only MCP messages. Anything meant for
 * a human goes to standard error.
 */
public final class DeploymentMcpServer {

    static final String SERVER_NAME = "helio-deployments";
    static final String SERVER_VERSION = "1.0.0";
    static final String TOOL_NAME = "getRecentDeployment";

    /**
     * The published input contract. The SDK validates every tools/call
     * against it before the handler runs (JSON Schema 2020-12, the spec
     * default): a missing, non-string, empty, or extra argument never reaches
     * the handler.
     */
    static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "serviceName", Map.of(
                            "type", "string",
                            "minLength", 1,
                            "maxLength", 64,
                            "description", "The name of a Helio platform service, for example \"notifications\".")),
            "required", List.of("serviceName"),
            "additionalProperties", false);

    /**
     * The published output contract for {@code structuredContent}: either a
     * deployment (version, deployedAt, summary) or an explicit
     * {@code recentDeployment: "none within the last 7 days"}. The SDK checks
     * every successful result against it before sending.
     */
    static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "serviceName", Map.of("type", "string"),
                    "version", Map.of("type", "string"),
                    "deployedAt", Map.of("type", "string"),
                    "summary", Map.of("type", "string"),
                    "recentDeployment", Map.of("type", "string")),
            "required", List.of("serviceName"),
            "additionalProperties", false);

    private DeploymentMcpServer() {
    }

    public static void main(String[] args) {
        McpJsonMapper json = McpJsonDefaults.getMapper();
        McpServer.sync(new StdioServerTransportProvider(json))
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(recentDeploymentTool(json))
                .build();
        // The transport's reader thread keeps this process alive. It exits when
        // the client closes standard input or terminates the process.
        System.err.println(SERVER_NAME + " MCP server: reading JSON-RPC from standard input.");
    }

    static SyncToolSpecification recentDeploymentTool(McpJsonMapper json) {
        Tool tool = Tool.builder(TOOL_NAME, INPUT_SCHEMA)
                .title("Recent deployment")
                .description("Returns the most recent deployment of a known Helio platform service, if any.")
                .outputSchema(OUTPUT_SCHEMA)
                // Hints about behavior. The spec is explicit that clients must
                // treat annotations from servers they do not trust as untrusted;
                // the agent application does not base any decision on them.
                .annotations(ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(json, request))
                .build();
    }

    /**
     * Domain validation and execution. The schema already guaranteed the
     * shape; this checks what only the deployment team knows — whether the
     * service exists. Failures are tool execution errors ({@code isError}),
     * reported in the result as the spec requires, never as a fake success.
     */
    static CallToolResult handle(McpJsonMapper json, CallToolRequest request) {
        Object value = request.arguments() == null ? null : request.arguments().get("serviceName");
        if (!(value instanceof String serviceName) || serviceName.isBlank()) {
            return error("'serviceName' must be a non-blank string.");
        }
        Map<String, Object> deployment;
        try {
            deployment = DeploymentRecords.recentDeployment(serviceName);
        } catch (IllegalArgumentException unknown) {
            return error(unknown.getMessage());
        }
        // Structured content for programs, plus the same JSON as text for
        // clients that only read text content (the spec's recommendation).
        return CallToolResult.builder()
                .structuredContent(deployment)
                .addTextContent(toJson(json, deployment))
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(message)
                .build();
    }

    private static String toJson(McpJsonMapper json, Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
