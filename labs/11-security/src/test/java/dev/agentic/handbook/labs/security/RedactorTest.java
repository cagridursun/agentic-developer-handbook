package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RedactorTest {

    private static final String TOKEN = HelioPlatform.FAKE_API_TOKEN;
    private final Redactor redactor = new Redactor(Set.of(TOKEN));

    @Test
    void aSecretValueTheApplicationKnowsIsRemovedEvenWithNoLabel() {
        assertEquals("the value is [REDACTED] ok", redactor.text("the value is " + TOKEN + " ok"));
    }

    @Test
    void aSecretNamedKeyHasItsWholeValueRemoved() {
        assertEquals(Redactor.REDACTED, redactor.value("monitoringApiToken", "anything"));
        assertEquals(Redactor.REDACTED, redactor.value("password", "anything"));
        assertEquals("30", redactor.value("input_tokens", "30"), "a count is not a secret");
    }

    @Test
    void knownSecretShapesAreRemoved() {
        assertFalse(redactor.text("Authorization: Bearer abc.def.ghi").contains("abc.def"));
        assertFalse(redactor.text("login password=hunter2 now").contains("hunter2"));
        assertFalse(redactor.text("client_secret: s3cr3t!").contains("s3cr3t"));
    }

    @Test
    void describeRedactsSecretNamedArguments() {
        String summary = redactor.describe(Map.of("api_key", "k-123"));
        assertEquals("api_key=[REDACTED]", summary);
    }

    @Test
    void longValuesAreShortenedForTelemetryButNotWhenScrubbingAResult() {
        String long300 = "a".repeat(300);
        assertEquals(Redactor.MAX_LENGTH, redactor.text(long300).length());
        assertEquals(300, redactor.redactSecrets(long300).length());
    }

    /**
     * A pinned limitation, not a feature: redaction by keyword, shape, and registered value is incomplete.
     * A secret in a shape the redactor does not know passes through. If this test ever fails because the
     * redactor became smarter, update the README's claim about what it misses, not this comment.
     */
    @Test
    void aSecretInAnUnregisteredOrReEncodedShapePassesThrough() {
        String encoded = Base64.getEncoder().encodeToString(TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(redactor.text("note " + encoded).contains(encoded));
        String split = TOKEN.substring(0, 10) + " " + TOKEN.substring(10);
        assertTrue(redactor.text(split).contains(TOKEN.substring(0, 10)));
        // An unregistered secret in prose is not recognized either.
        assertTrue(redactor.text("the vault phrase is open sesame 7731").contains("open sesame 7731"));
        // And personal data is not recognized at all.
        assertTrue(redactor.text("contact jane.doe@example.com").contains("jane.doe@example.com"));
    }
}
