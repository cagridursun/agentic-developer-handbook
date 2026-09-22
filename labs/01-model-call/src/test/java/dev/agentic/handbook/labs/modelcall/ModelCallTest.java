package dev.agentic.handbook.labs.modelcall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The lab's only local logic is configuration resolution. The real model call is
 * exercised manually, so normal CI never needs an API key.
 */
class ModelCallTest {

    @Test
    void apiKeyComesFromGoogleApiKey() {
        Optional<String> key = ModelCall.apiKey(Map.of("GOOGLE_API_KEY", "abc"));
        assertEquals(Optional.of("abc"), key);
    }

    @Test
    void legacyGeminiApiKeyIsAccepted() {
        Optional<String> key = ModelCall.apiKey(Map.of("GEMINI_API_KEY", "legacy"));
        assertEquals(Optional.of("legacy"), key);
    }

    @Test
    void googleApiKeyTakesPrecedenceOverLegacyName() {
        // Matches the official SDK rule: if both are set, GOOGLE_API_KEY wins.
        Optional<String> key = ModelCall.apiKey(Map.of(
                "GOOGLE_API_KEY", "primary",
                "GEMINI_API_KEY", "legacy"));
        assertEquals(Optional.of("primary"), key);
    }

    @Test
    void missingKeyIsEmptyNotAnError() {
        assertTrue(ModelCall.apiKey(Map.of()).isEmpty());
    }

    @Test
    void blankKeyCountsAsMissing() {
        assertTrue(ModelCall.apiKey(Map.of("GOOGLE_API_KEY", "   ")).isEmpty());
    }

    @Test
    void modelDefaultsWhenUnset() {
        assertEquals(ModelCall.DEFAULT_MODEL, ModelCall.model(Map.of()));
    }

    @Test
    void modelCanBeOverriddenByEnvironment() {
        assertEquals("gemini-3.5-flash-lite", ModelCall.model(Map.of("GEMINI_MODEL", "gemini-3.5-flash-lite")));
    }

    @Test
    void blankModelFallsBackToDefault() {
        assertEquals(ModelCall.DEFAULT_MODEL, ModelCall.model(Map.of("GEMINI_MODEL", " ")));
    }
}
