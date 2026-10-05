package dev.agentic.handbook.labs.deployment;

import static dev.agentic.handbook.labs.deployment.TestSupport.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentic.handbook.labs.deployment.TestSupport.Running;
import dev.agentic.handbook.labs.security.HelioPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HealthAndReadinessTest {

    private Running running;

    @AfterEach
    void stop() {
        if (running != null) {
            running.app().shutdown();
        }
    }

    @Test
    void healthyStartupIsAliveAndReady() throws Exception {
        running = TestSupport.start();
        assertEquals(new TestSupport.Reply(200, "{\"status\":\"UP\"}"), get(running, "/health"));
        TestSupport.Reply ready = get(running, "/ready");
        assertEquals(200, ready.status());
        assertEquals("{\"status\":\"READY\",\"checks\":{\"lifecycle\":\"UP\",\"authorization\":\"ENFORCED\"}}", ready.body());
        assertEquals(State.READY, running.app().state());
    }

    @Test
    void responsesAreDeterministic() throws Exception {
        running = TestSupport.start();
        for (String path : new String[] {"/health", "/ready", "/info"}) {
            assertEquals(get(running, path), get(running, path), path);
        }
    }

    @Test
    void infoSaysWhichArtifactAndEnvironmentIsRunning() throws Exception {
        running = TestSupport.start("APP_ENV", "demo");
        String body = get(running, "/info").body();
        assertTrue(TestSupport.contains(body, "\"version\":\"0.1.0-test\"", "\"build\":\"build-test\"",
                "\"environment\":\"demo\"", "\"app\":\"lab-12-deployment\""), body);
    }

    @Test
    void theBuildIdentityComesFromThePackagedResourceNotFromTheEnvironment() {
        BuildInfo build = BuildInfo.load();
        assertEquals("lab-12-deployment", build.app());
        assertFalse(build.version().contains("${"), "Maven must have filled the version in");
        assertFalse(build.buildId().contains("${"), "Maven must have filled the build id in");
    }

    @Test
    void healthAndReadinessNeverLeakASecret() throws Exception {
        String token = "test-only-placeholder-token-123";
        running = TestSupport.start("APP_MONITORING_TOKEN", token, "APP_LOG_LEVEL", "DEBUG");
        for (String path : new String[] {"/health", "/ready", "/info", "/nothing-here"}) {
            String body = get(running, path).body();
            assertFalse(body.contains(token), path);
            assertFalse(body.contains(HelioPlatform.FAKE_API_TOKEN), path);
            assertFalse(body.toLowerCase().contains("token"), path);
        }
        assertFalse(running.logText().contains(token));
    }

    @Test
    void unknownPathsAndWrongMethodsAreRefusedWithoutEchoingInput() throws Exception {
        running = TestSupport.start("APP_LOG_LEVEL", "DEBUG");
        TestSupport.Reply unknown = get(running, "/admin/%0Afake-log-line");
        assertEquals(404, unknown.status());
        assertEquals("{\"error\":\"NOT_FOUND\"}", unknown.body());
        assertFalse(running.logText().contains("fake-log-line"), "an outsider's path is not copied into the log");
        TestSupport.Reply wrongMethod = TestSupport.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(running.url("/health"))).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(405, wrongMethod.status());
    }

    @Test
    void probesAreLoggedAtDebugOnly() throws Exception {
        running = TestSupport.start();
        get(running, "/health");
        get(running, "/ready");
        assertFalse(running.logText().contains("HEALTH_CHECK"), "INFO level does not record every probe");
        running.app().shutdown();
        running = TestSupport.start("APP_LOG_LEVEL", "DEBUG");
        get(running, "/health");
        get(running, "/ready");
        assertTrue(TestSupport.contains(running.logText(), "\"event\":\"HEALTH_CHECK\"", "\"event\":\"READINESS_CHECK\""));
    }
}
