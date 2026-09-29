package dev.agentic.handbook.labs.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This application's MCP client for the deployment team's server. It is the
 * only class on the agent side that touches MCP SDK types — the runtime and the
 * model never see them.
 *
 * <p>What it does, in order:
 * <ol>
 *   <li>launches the server as a separate process and connects over STDIO;</li>
 *   <li>runs the MCP {@code initialize} handshake (protocol version negotiation);</li>
 *   <li>discovers the server's tools with {@code tools/list};</li>
 *   <li>keeps only the tools on this application's allowlist — discovery is not permission;</li>
 *   <li>calls the one approved tool when the runtime asks, and translates the
 *       result into the runtime's ordinary observation, or fails clearly.</li>
 * </ol>
 *
 * <p>There is deliberately no generic {@code callTool(name, args)} here: the
 * only remote call this application can make is the one it approved and wrote
 * a method for.
 */
public final class DeploymentMcpClient implements AutoCloseable {

    /** The deployment team's server. A class name, not an import: the agent does not link against it. */
    static final String SERVER_MAIN_CLASS = "dev.agentic.handbook.labs.mcp.server.DeploymentMcpServer";

    /** The remote tool this application calls. */
    static final String RECENT_DEPLOYMENT = "getRecentDeployment";

    /**
     * The application's allowlist of remote tools. Written by the application's
     * owners and changed only by a code change here. Nothing a server announces
     * can add to it.
     */
    static final Set<String> APPROVED_TOOLS = Set.of(RECENT_DEPLOYMENT);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final McpSyncClient client;
    private final Discovery discovery;
    private int toolCallsSent;

    /**
     * What the application learned when it connected, and what it decided.
     *
     * @param announced every tool the server listed, in the server's order
     * @param approved  the announced tools this application allows
     * @param ignored   announced tools that stay invisible to the runtime and the model
     */
    public record Discovery(
            String serverName,
            String serverVersion,
            String protocolVersion,
            List<String> announced,
            List<String> approved,
            List<String> ignored) {
    }

    private DeploymentMcpClient(McpSyncClient client, Discovery discovery) {
        this.client = client;
        this.discovery = discovery;
    }

    /** Launches the deployment team's MCP server and connects to it. */
    public static DeploymentMcpClient launch() {
        return launch(SERVER_MAIN_CLASS);
    }

    /** Launches a given server main class (tests use servers that misbehave on purpose). */
    static DeploymentMcpClient launch(String serverMainClass, String... serverArgs) {
        // STDIO: the client starts the server as a child process and speaks
        // JSON-RPC over its standard input and output. The server ships in this
        // Maven module only so the lab runs with one command; it is started
        // with the same JVM and classpath, as a separate process.
        List<String> args = new ArrayList<>(List.of(
                "-cp", System.getProperty("java.class.path"), serverMainClass));
        args.addAll(List.of(serverArgs));
        ServerParameters server = ServerParameters.builder(javaExecutable()).args(args).build();

        McpSyncClient client = McpClient.sync(new StdioClientTransport(server, McpJsonDefaults.getMapper()))
                .clientInfo(Implementation.builder("helio-incident-agent", "1.0.0").build())
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(REQUEST_TIMEOUT)
                // Remember each announced outputSchema and check structured
                // results against it, as the spec says clients should.
                .enableCallToolSchemaCaching(true)
                .build();
        try {
            InitializeResult session = client.initialize();
            // The sync client follows tools/list pagination cursors itself.
            Discovery discovery = discover(session, client.listTools().tools());
            return new DeploymentMcpClient(client, discovery);
        } catch (RuntimeException failure) {
            client.closeGracefully();
            throw failure;
        }
    }

    /**
     * Applies the allowlist to what the server announced. Fails at startup —
     * not at the first model decision — when an approved tool is missing or its
     * contract no longer matches what this application was written against.
     */
    static Discovery discover(InitializeResult session, List<Tool> tools) {
        List<String> announced = tools.stream().map(Tool::name).toList();
        List<String> approved = announced.stream().filter(APPROVED_TOOLS::contains).toList();
        List<String> ignored = announced.stream().filter(name -> !APPROVED_TOOLS.contains(name)).toList();

        for (String required : APPROVED_TOOLS) {
            Tool tool = tools.stream().filter(t -> t.name().equals(required)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The MCP server '"
                            + session.serverInfo().name() + "' does not announce the approved tool '"
                            + required + "'. Announced: " + announced));
            requireServiceNameContract(tool);
        }
        return new Discovery(session.serverInfo().name(), session.serverInfo().version(),
                session.protocolVersion(), announced, approved, ignored);
    }

    /** The approved tool must still take a required string 'serviceName'. */
    private static void requireServiceNameContract(Tool tool) {
        Map<String, Object> schema = tool.inputSchema();
        boolean declared = schema.get("properties") instanceof Map<?, ?> properties
                && properties.get("serviceName") instanceof Map<?, ?> property
                && "string".equals(property.get("type"));
        boolean required = schema.get("required") instanceof List<?> names && names.contains("serviceName");
        if (!declared || !required) {
            throw new IllegalStateException("The announced contract of '" + tool.name()
                    + "' changed: it no longer requires a string 'serviceName'. Input schema: " + schema);
        }
    }

    public Discovery discovery() {
        return discovery;
    }

    /** How many tools/call requests this client has sent. Lets tests and the demo prove what crossed the boundary. */
    public int toolCallsSent() {
        return toolCallsSent;
    }

    /**
     * Calls the approved remote tool and translates its result into an
     * ordinary observation. The server's answer is untrusted input: only a
     * successful, structured result becomes an observation; everything else is
     * a {@link RemoteToolException} with the reason.
     */
    public Map<String, Object> getRecentDeployment(String serviceName) {
        CallToolRequest request = CallToolRequest.builder(RECENT_DEPLOYMENT)
                .arguments(Map.of("serviceName", serviceName))
                .build();
        toolCallsSent++;
        CallToolResult result;
        try {
            result = client.callTool(request);
        } catch (McpError protocolError) {
            throw new RemoteToolException("MCP protocol error from '" + discovery.serverName() + "': "
                    + describe(protocolError), protocolError);
        } catch (RuntimeException unreachable) {
            throw new RemoteToolException("MCP request to '" + discovery.serverName() + "' failed: "
                    + unreachable.getMessage(), unreachable);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new RemoteToolException("MCP tool error from '" + discovery.serverName() + "': " + text(result));
        }
        if (!(result.structuredContent() instanceof Map<?, ?> structured)) {
            throw new RemoteToolException("'" + RECENT_DEPLOYMENT + "' returned no structured result; "
                    + "this application does not guess facts from free text.");
        }
        Map<String, Object> observation = new LinkedHashMap<>();
        structured.forEach((key, value) -> observation.put(String.valueOf(key), value));
        return Collections.unmodifiableMap(observation);
    }

    /** Stops the session; the STDIO transport terminates the server process. */
    @Override
    public void close() {
        client.closeGracefully();
    }

    static String describe(McpError error) {
        var rpc = error.getJsonRpcError();
        if (rpc == null) {
            return error.getMessage();
        }
        return "JSON-RPC " + rpc.code() + " " + rpc.message() + (rpc.data() == null ? "" : " (" + rpc.data() + ")");
    }

    private static String text(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining(" "));
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
