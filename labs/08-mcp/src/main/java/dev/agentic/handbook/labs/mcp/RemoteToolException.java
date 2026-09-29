package dev.agentic.handbook.labs.mcp;

/**
 * An allowed, validated request reached the MCP boundary and did not produce a
 * usable result: the server reported a tool execution error, returned a
 * protocol error, sent a result this application cannot use, or could not be
 * reached. The runtime turns it into {@link StopReason#TOOL_FAILED}.
 */
public final class RemoteToolException extends RuntimeException {

    public RemoteToolException(String message) {
        super(message);
    }

    public RemoteToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
