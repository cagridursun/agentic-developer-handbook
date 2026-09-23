package dev.agentic.handbook.labs.toolcalling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the trust boundary: the catalog itself, and the
 * validation that stands between a model-generated tool request and the Java
 * method. The real two-call Gemini flow is a manual smoke test; CI needs no
 * API key and no network.
 */
class ToolCallingExampleTest {

    // --- The tool implementation: plain Java, no model anywhere. ---

    @Test
    void knownServiceReturnsItsStatus() {
        ServiceStatus status = ServiceCatalog.getServiceStatus("authorization").orElseThrow();
        assertEquals(ServiceState.HEALTHY, status.state());
        assertEquals("authorization", status.serviceName());
    }

    @Test
    void lookupNormalizesCaseAndWhitespace() {
        ServiceStatus status = ServiceCatalog.getServiceStatus("  Search ").orElseThrow();
        assertEquals(ServiceState.MAINTENANCE, status.state());
    }

    @Test
    void unknownServiceIsEmpty() {
        assertTrue(ServiceCatalog.getServiceStatus("payments").isEmpty());
        assertTrue(ServiceCatalog.getServiceStatus(null).isEmpty());
    }

    // --- The trust boundary: model-proposed requests are untrusted input. ---

    @Test
    void validRequestExecutesTheToolAndReturnsAStructuredResult() {
        Map<String, Object> result = ToolCallingExample.handleToolRequest(
                "getServiceStatus", Map.of("serviceName", "notifications"));

        assertEquals("notifications", result.get("serviceName"));
        assertEquals("DEGRADED", result.get("status"));
        assertEquals("Email delivery is delayed by up to 10 minutes.", result.get("message"));
    }

    @Test
    void toolNamesOutsideTheAllowlistAreRejected() {
        // The model must not be able to pick arbitrary application capabilities.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest(
                        "deleteAllServices", Map.of("serviceName", "billing")));
        assertTrue(e.getMessage().contains("deleteAllServices"));
    }

    @Test
    void missingArgumentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest("getServiceStatus", Map.of()));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest("getServiceStatus", null));
    }

    @Test
    void blankServiceNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest(
                        "getServiceStatus", Map.of("serviceName", "   ")));
    }

    @Test
    void nonStringServiceNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest(
                        "getServiceStatus", Map.of("serviceName", 42)));
    }

    @Test
    void unknownServiceBecomesAnErrorResultForTheModel() {
        // A well-formed request for a service we do not have is not an application
        // bug: the tool result tells the model, and the model tells the user.
        Map<String, Object> result = ToolCallingExample.handleToolRequest(
                "getServiceStatus", Map.of("serviceName", "payments"));

        assertTrue(result.containsKey("error"));
        assertEquals(List.copyOf(ServiceCatalog.knownServices()), result.get("knownServices"));
    }

    @Test
    void extraUnexpectedArgumentsAreRejected() {
        Map<String, Object> args = new HashMap<>();
        args.put("serviceName", "billing");
        args.put("dropTables", true);
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingExample.handleToolRequest("getServiceStatus", args));
    }

    // --- Configuration, same semantics as the earlier labs. ---

    @Test
    void apiKeyAndModelResolution() {
        assertEquals("primary", ToolCallingExample.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy")).orElseThrow());
        assertTrue(ToolCallingExample.apiKey(Map.of()).isEmpty());
        assertEquals(ToolCallingExample.DEFAULT_MODEL, ToolCallingExample.model(Map.of()));
        assertEquals("gemini-3.5-flash-lite",
                ToolCallingExample.model(Map.of("GEMINI_MODEL", "gemini-3.5-flash-lite")));
    }
}
