package dev.agentic.handbook.labs.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DeploymentConfigTest {

    @Test
    void defaultsApplyWhenOnlyTheRequiredSettingIsGiven() {
        DeploymentConfig config = DeploymentConfig.fromEnvironment(Map.of("APP_ENV", "local"));
        assertEquals("local", config.environment());
        assertEquals(8080, config.port());
        assertEquals("127.0.0.1", config.bindAddress(), "the default listens on loopback only");
        assertEquals(LogLevel.INFO, config.logLevel());
        assertEquals(DeploymentConfig.registeredTools(), List.copyOf(config.enabledTools()));
        assertEquals(10, config.shutdownGraceSeconds());
        assertTrue(config.expectedVersion().isEmpty());
        assertTrue(config.monitoringToken().isEmpty());
    }

    @Test
    void customValuesChangeBehaviorWithoutAnySourceChange() {
        DeploymentConfig config = TestSupport.config("APP_ENV", "demo", "APP_PORT", "9090",
                "APP_BIND_ADDRESS", "0.0.0.0", "APP_LOG_LEVEL", "DEBUG", "APP_TOOLS", "getServiceStatus",
                "APP_SHUTDOWN_GRACE_SECONDS", "3", "APP_EXPECTED_VERSION", "1.2.3");
        assertEquals("demo", config.environment());
        assertEquals(9090, config.port());
        assertEquals("0.0.0.0", config.bindAddress());
        assertEquals(LogLevel.DEBUG, config.logLevel());
        assertEquals(List.of("getServiceStatus"), List.copyOf(config.enabledTools()));
        assertEquals(3, config.shutdownGraceSeconds());
        assertEquals("1.2.3", config.expectedVersion().orElseThrow());
    }

    @Test
    void aMissingRequiredSettingFailsFast() {
        ConfigException e = assertThrows(ConfigException.class, () -> DeploymentConfig.fromEnvironment(Map.of()));
        assertTrue(e.getMessage().contains("APP_ENV is required"), e.getMessage());
    }

    @Test
    void invalidValuesAreRejectedAndEveryProblemIsReportedAtOnce() {
        ConfigException e = assertThrows(ConfigException.class, () -> TestSupport.config(
                "APP_PORT", "70000", "APP_LOG_LEVEL", "LOUD", "APP_BIND_ADDRESS", "not-an-address",
                "APP_TOOLS", "getServiceStatus,deleteEverything", "APP_SHUTDOWN_GRACE_SECONDS", "-1",
                "APP_MODE", "live"));
        for (String setting : List.of("APP_PORT", "APP_LOG_LEVEL", "APP_BIND_ADDRESS", "APP_TOOLS",
                "APP_SHUTDOWN_GRACE_SECONDS", "APP_MODE")) {
            assertTrue(e.getMessage().contains(setting), setting + " in: " + e.getMessage());
        }
    }

    @Test
    void aPortThatIsNotANumberIsRejected() {
        assertThrows(ConfigException.class, () -> TestSupport.config("APP_PORT", "eighty"));
    }

    @Test
    void aSettingThatWouldWeakenAuthorizationIsRejectedNotIgnored() {
        for (String attempt : List.of("APP_AUTHORIZATION", "APP_DISABLE_AUTHORIZATION", "APP_AUTH_DISABLED",
                "APP_SKIP_APPROVAL", "APP_POLICY", "APP_ALLOW_ALL")) {
            ConfigException e = assertThrows(ConfigException.class, () -> TestSupport.config(attempt, "off"), attempt);
            assertTrue(e.getMessage().contains("unknown setting " + attempt), e.getMessage());
        }
    }

    @Test
    void configurationCannotAddAToolTheApplicationDidNotRegister() {
        assertThrows(ConfigException.class, () -> TestSupport.config("APP_TOOLS", "executeShell"));
        assertThrows(ConfigException.class, () -> TestSupport.config("APP_TOOLS", ""));
    }

    @Test
    void errorMessagesNameSettingsButNeverValues() {
        String secretLooking = "sk-not-a-real-value-0123456789abcdef";
        ConfigException e = assertThrows(ConfigException.class, () -> TestSupport.config(
                "APP_PORT", secretLooking, "APP_LOG_LEVEL", secretLooking, "APP_ENV", "UPPER " + secretLooking));
        assertFalse(e.getMessage().contains(secretLooking), e.getMessage());
    }

    @Test
    void aBlankSecretIsRejectedAndAPresentOneIsNeverPrinted() {
        assertThrows(ConfigException.class, () -> TestSupport.config("APP_MONITORING_TOKEN", "  "));
        String token = "test-only-placeholder-token-123";
        DeploymentConfig config = TestSupport.config("APP_MONITORING_TOKEN", token);
        assertTrue(config.monitoringToken().isPresent());
        assertFalse(config.describe().contains(token));
        assertFalse(config.toString().contains(token), "the record's own string form must not print the secret");
        assertTrue(config.describe().contains("monitoringToken=set (hidden)"));
    }

    @Test
    void anArtifactThatIsNotTheExpectedOneIsRefused() {
        DeploymentConfig config = TestSupport.config("APP_EXPECTED_VERSION", "9.9.9");
        ConfigException e = assertThrows(ConfigException.class, () -> config.checkAgainst(TestSupport.BUILD));
        assertTrue(e.getMessage().contains("0.1.0-test"), e.getMessage());
        TestSupport.config("APP_EXPECTED_VERSION", "0.1.0-test").checkAgainst(TestSupport.BUILD);
    }

    @Test
    void theSampleConfigurationFilesAreValidAndHoldNoSecret() throws IOException {
        Path dir = Path.of("config");
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> samples = files.filter(path -> path.toString().endsWith(".env")).sorted().toList();
            assertEquals(List.of("demo.env", "local.env", "test.env"),
                    samples.stream().map(path -> path.getFileName().toString()).toList());
            for (Path sample : samples) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String line : Files.readAllLines(sample)) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        String[] pair = line.split("=", 2);
                        values.put(pair[0], pair[1]);
                    }
                }
                DeploymentConfig.fromEnvironment(values);
                assertFalse(values.containsKey("APP_MONITORING_TOKEN"), sample + " must not carry a secret");
                assertFalse(Files.readString(sample).toLowerCase().matches("(?s).*(password|api[_-]?key|bearer|sk-).*"),
                        sample.toString());
            }
        }
    }
}
