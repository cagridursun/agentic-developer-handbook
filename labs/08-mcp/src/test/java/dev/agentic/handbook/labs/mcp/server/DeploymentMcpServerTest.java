package dev.agentic.handbook.labs.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The MCP server on its own, spoken to with the raw official SDK client over a
 * real STDIO process — no application code, no runtime, no mocks. This is the
 * server's contract as any compatible client would see it.
 */
class DeploymentMcpServerTest {

    private static McpSyncClient client;
    private static InitializeResult session;

    @BeforeAll
    static void startServer() {
        ServerParameters server = ServerParameters.builder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .args("-cp", System.getProperty("java.class.path"), DeploymentMcpServer.class.getName())
                .build();
        client = McpClient.sync(new StdioClientTransport(server, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
        session = client.initialize();
    }

    @AfterAll
    static void stopServer() {
        client.closeGracefully();
    }

    // --- Initialization and discovery ---

    @Test
    void initializeNegotiatesTheStableSdkProtocolVersion() {
        assertEquals("2025-11-25", session.protocolVersion());
        assertEquals("helio-deployments", session.serverInfo().name());
        assertEquals("1.0.0", session.serverInfo().version());
    }

    @Test
    void serverDeclaresToolsAndNothingElse() {
        McpSchema.ServerCapabilities capabilities = session.capabilities();
        assertNotNull(capabilities.tools());
        // No resources, prompts, or completions: one boundary, taught well.
        // (SDK 2.0.1 always advertises the logging capability itself; this
        // server never sends log messages.)
        assertNull(capabilities.resources());
        assertNull(capabilities.prompts());
        assertNull(capabilities.completions());
    }

    @Test
    void toolsListAnnouncesExactlyOneToolWithAClearInputSchema() {
        List<Tool> tools = client.listTools().tools();
        assertEquals(1, tools.size());
        Tool tool = tools.get(0);
        assertEquals("getRecentDeployment", tool.name());
        assertNotNull(tool.description());

        Map<String, Object> schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        assertEquals(List.of("serviceName"), schema.get("required"));
        assertEquals(false, schema.get("additionalProperties"));
        Map<?, ?> serviceName = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("serviceName");
        assertEquals("string", serviceName.get("type"));

        assertEquals("object", tool.outputSchema().get("type"));
        // A hint, not a guarantee — the application does not rely on it.
        assertTrue(tool.annotations().readOnlyHint());
    }

    // --- Calls: deterministic structured data ---

    @Test
    void validCallReturnsDeterministicStructuredData() {
        CallToolResult result = call(Map.of("serviceName", "notifications"));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        Map<?, ?> deployment = (Map<?, ?>) result.structuredContent();
        assertEquals("notifications-2.4.1", deployment.get("version"));
        assertEquals("2026-09-23T13:52:00Z", deployment.get("deployedAt"));
        // The same data as text, for clients that only read text content.
        assertTrue(text(result).contains("notifications-2.4.1"));
    }

    @Test
    void knownServiceWithoutDeploymentSaysSoExplicitly() {
        Map<?, ?> result = (Map<?, ?>) call(Map.of("serviceName", "billing")).structuredContent();
        assertEquals("none within the last 7 days", result.get("recentDeployment"));
    }

    // --- Input validation: two layers, both on the server ---

    @Test
    void schemaInvalidArgumentsNeverReachTheHandler() {
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of(),
                Map.of("serviceName", 42),
                Map.of("serviceName", ""),
                Map.of("serviceName", "billing", "dropTables", true))) {
            CallToolResult result = call(invalid);
            assertTrue(result.isError(), invalid.toString());
            assertTrue(text(result).contains("input validation failed"), text(result));
        }
    }

    @Test
    void unknownServiceIsAToolExecutionErrorNotAnEmptySuccess() {
        CallToolResult result = call(Map.of("serviceName", "payments"));
        assertTrue(result.isError());
        assertNull(result.structuredContent());
        assertTrue(text(result).contains("Unknown service 'payments'"), text(result));
    }

    @Test
    void unknownToolIsAProtocolError() {
        McpError error = assertThrows(McpError.class, () -> client.callTool(
                CallToolRequest.builder("rollbackDeployment")
                        .arguments(Map.of("serviceName", "notifications"))
                        .build()));
        assertEquals(McpSchema.ErrorCodes.INVALID_PARAMS, error.getJsonRpcError().code());
    }

    // --- The handler and the data, without any protocol ---

    @Test
    void handlerValidatesTheDomainItOwns() {
        var json = McpJsonDefaults.getMapper();
        assertTrue(DeploymentMcpServer.handle(json, request(Map.of("serviceName", " "))).isError());
        assertTrue(DeploymentMcpServer.handle(json, request(Map.of("serviceName", "payments"))).isError());
        assertFalse(DeploymentMcpServer.handle(json, request(Map.of("serviceName", "  Notifications "))).isError());
    }

    @Test
    void recordsAreDeterministic() {
        assertEquals("notifications-2.4.1", DeploymentRecords.recentDeployment("notifications").get("version"));
        assertEquals(List.of("serviceName", "version", "deployedAt", "summary"),
                List.copyOf(DeploymentRecords.recentDeployment("notifications").keySet()));
        assertThrows(IllegalArgumentException.class, () -> DeploymentRecords.recentDeployment("payments"));
    }

    private static CallToolResult call(Map<String, Object> arguments) {
        return client.callTool(CallToolRequest.builder("getRecentDeployment").arguments(arguments).build());
    }

    private static CallToolRequest request(Map<String, Object> arguments) {
        return CallToolRequest.builder("getRecentDeployment").arguments(arguments).build();
    }

    private static String text(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .reduce("", String::concat);
    }
}
