package dev.agentic.handbook.labs.mcp.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test-only MCP servers that do what a server owned by someone else might do:
 * announce more than the application approved, drop a tool, change a
 * contract, fail inside a handler, or answer with text only. Each is a real
 * STDIO server process, so the application's client is tested against the
 * protocol, not against a mock.
 */
public final class MisbehavingDeploymentServer {

    private MisbehavingDeploymentServer() {
    }

    public static void main(String[] args) {
        McpJsonMapper json = McpJsonDefaults.getMapper();
        McpServer.sync(new StdioServerTransportProvider(json))
                .serverInfo("misbehaving-deployments", "0.0.1")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools(json, args[0]))
                .build();
    }

    private static List<SyncToolSpecification> tools(McpJsonMapper json, String mode) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        switch (mode) {
            // The real tool, plus one the application never approved.
            case "extra-tool" -> {
                tools.add(DeploymentMcpServer.recentDeploymentTool(json));
                tools.add(tool(Tool.builder("rollbackDeployment", DeploymentMcpServer.INPUT_SCHEMA)
                        .description("Rolls a service back to its previous version.")
                        .build(),
                        (request) -> {
                            throw new IllegalStateException("rollbackDeployment must never be called in this lab.");
                        }));
            }
            // The approved tool is gone; only an unapproved one is announced.
            case "missing-tool" -> tools.add(tool(Tool.builder("rollbackDeployment", DeploymentMcpServer.INPUT_SCHEMA)
                    .build(), (request) -> text("unreachable")));
            // Same name, different input contract.
            case "changed-contract" -> tools.add(tool(Tool.builder("getRecentDeployment", Map.of(
                    "type", "object",
                    "properties", Map.of("service", Map.of("type", "string")),
                    "required", List.of("service"))).build(), (request) -> text("unreachable")));
            // The handler throws: the SDK answers with a JSON-RPC error.
            case "crashing-tool" -> tools.add(tool(Tool.builder("getRecentDeployment", DeploymentMcpServer.INPUT_SCHEMA)
                    .build(), (request) -> {
                        throw new IllegalStateException("deployment database unavailable");
                    }));
            // A successful result with no structured content, only prose.
            case "text-only" -> tools.add(tool(Tool.builder("getRecentDeployment", DeploymentMcpServer.INPUT_SCHEMA)
                    .build(), (request) -> text("I think something was deployed recently, probably.")));
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
        return tools;
    }

    private interface Handler {
        CallToolResult handle(Map<String, Object> arguments);
    }

    private static SyncToolSpecification tool(Tool tool, Handler handler) {
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.handle(request.arguments()))
                .build();
    }

    private static CallToolResult text(String text) {
        return CallToolResult.builder().addTextContent(text).build();
    }
}
