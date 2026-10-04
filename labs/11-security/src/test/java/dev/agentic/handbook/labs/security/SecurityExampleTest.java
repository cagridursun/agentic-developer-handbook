package dev.agentic.handbook.labs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentic.handbook.labs.security.vulnerable.VulnerableDemo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SecurityExampleTest {

    private static String capture(Consumer<PrintStream> run) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        run.accept(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theDefaultRunNeedsNoKeyNetworkOrServerAndTeachesTheControls() {
        String output = capture(SecurityExample::run);
        assertTrue(output.contains("DENIED(CAPABILITY_NOT_GRANTED)"));
        assertTrue(output.contains("DENIED(RESOURCE_OUT_OF_SCOPE)"));
        assertTrue(output.contains("DENIED(NO_PRINCIPAL)"));
        assertTrue(output.contains("INVALID_ARGUMENTS("));
        assertTrue(output.contains("APPROVAL_REQUIRED(STATE_CHANGE_NEEDS_APPROVAL)"));
        assertTrue(output.contains("APPROVAL_INVALID(APPROVAL_DOES_NOT_MATCH_TARGET)"));
        assertTrue(output.contains("state changes that really happened: [restart notifications strategy=ROLLING grace=30]"));
        assertTrue(output.contains("fake token appears anywhere in this output: false"));
        assertFalse(output.contains(HelioPlatform.FAKE_API_TOKEN));
    }

    @Test
    void theDefaultRunIsDeterministic() {
        assertEquals(capture(SecurityExample::run), capture(SecurityExample::run));
    }

    @Test
    void theVulnerableDemoShowsWhatTheControlsPrevent() {
        String output = capture(VulnerableDemo::run);
        assertTrue(output.contains("INTENTIONALLY UNSAFE"));
        assertTrue(output.contains("restart billing strategy=IMMEDIATE grace=0"));
        assertTrue(output.contains(HelioPlatform.FAKE_API_TOKEN));
    }

    @Test
    void theSecurePathNeverReferencesTheVulnerableCode() throws IOException {
        Path secure = Path.of("src/main/java/dev/agentic/handbook/labs/security");
        try (Stream<Path> files = Files.list(secure)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("security.vulnerable"), file + " must not depend on the vulnerable demo");
                assertFalse(source.contains("VulnerableAssistant"), file.toString());
            }
        }
        // And the vulnerable code says plainly what it is.
        try (Stream<Path> files = Files.list(secure.resolve("vulnerable"))) {
            for (Path file : files.toList()) {
                assertTrue(Files.readString(file).contains("INTENTIONALLY VULNERABLE"), file.toString());
            }
        }
    }
}
