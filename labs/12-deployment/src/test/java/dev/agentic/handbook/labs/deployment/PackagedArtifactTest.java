package dev.agentic.handbook.labs.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

/**
 * Checks the packaged artifact itself: the jar Maven built, not the classes in the
 * IDE. These tests run in the {@code verify} phase, after packaging (see the pom).
 * Running the jar as a process stands in for the container: the same artifact, the
 * same entry point, a real SIGTERM, no Docker.
 */
class PackagedArtifactTest {

    private static final Path JAR = Path.of("target", "lab-12-deployment.jar");
    private static final Path LIB = Path.of("target", "lib");

    @Test
    void theJarAndItsRuntimeDependencyExist() throws IOException {
        assertTrue(Files.isRegularFile(JAR), "run ./mvnw verify to build " + JAR);
        try (var files = Files.list(LIB)) {
            assertEquals(List.of("lab-11-security-0.1.0-SNAPSHOT.jar"),
                    files.map(path -> path.getFileName().toString()).toList(),
                    "the runtime needs the Lab 11 boundary and nothing else");
        }
    }

    @Test
    void theManifestNamesTheEntryPointAndTheClasspath() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            Manifest manifest = jar.getManifest();
            var attributes = manifest.getMainAttributes();
            assertEquals("dev.agentic.handbook.labs.deployment.DeploymentMain", attributes.getValue("Main-Class"));
            assertEquals("lib/lab-11-security-0.1.0-SNAPSHOT.jar", attributes.getValue("Class-Path"));
            assertEquals("27", attributes.getValue("Build-Jdk-Spec"));
        }
    }

    @Test
    void theArtifactCarriesItsOwnDeterministicIdentity() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            Properties properties = new Properties();
            properties.load(jar.getInputStream(jar.getEntry("deployment.properties")));
            assertEquals("lab-12-deployment", properties.getProperty("app.name"));
            assertEquals(jar.getManifest().getMainAttributes().getValue("Implementation-Version"),
                    properties.getProperty("app.version"), "the manifest and the resource agree");
            assertTrue(properties.getProperty("build.id").matches("[A-Za-z0-9._-]+"), properties.getProperty("build.id"));
            assertFalse(properties.getProperty("app.version").contains("${"));
        }
    }

    @Test
    void theJarContainsNoSecretsAndNoConfigurationForAnyEnvironment() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            for (var entry : jar.stream().toList()) {
                String name = entry.getName().toLowerCase();
                assertFalse(name.endsWith(".env") || name.endsWith(".pem") || name.endsWith(".key") || name.contains("secret"),
                        name);
                if (name.endsWith(".properties")) {
                    String text = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    assertFalse(text.toLowerCase().matches("(?s).*(password|token|api[_-]?key|secret).*"), name);
                }
            }
        }
    }

    @Test
    void theJarIsReproducible() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            var time = jar.getEntry("deployment.properties").getTimeLocal();
            assertEquals(java.time.LocalDateTime.parse("2026-01-01T00:00:00"), time, "entries carry the fixed build timestamp");
        }
    }

    @Test
    void theJarStartsServesProbesAndShutsDownGracefullyOnSigterm() throws Exception {
        Process refused = new ProcessBuilder(javaBinary(), "-jar", JAR.toString()).redirectErrorStream(true).start();
        refused.getOutputStream().close();
        String refusal = new String(refused.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(refused.waitFor(20, TimeUnit.SECONDS));
        assertEquals(2, refused.exitValue(), "no APP_ENV: refuses to start with the configuration exit code");
        assertTrue(refusal.contains("APP_ENV is required"), refusal);

        // The output goes to a file, so nothing is lost when the process exits.
        Path output = Files.createTempFile("lab-12-artifact-test", ".log");
        ProcessBuilder builder = new ProcessBuilder(javaBinary(), "-jar", JAR.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("APP_ENV", "test");
        builder.environment().put("APP_PORT", "0");
        Process running = builder.start();
        running.getOutputStream().close();
        int port = -1;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (port < 0 && System.nanoTime() < deadline) {
            for (String line : Files.readAllLines(output)) {
                if (line.contains("\"event\":\"STARTUP_COMPLETED\"")) {
                    port = Integer.parseInt(line.split("\"port\":\"")[1].split("\"")[0]);
                }
            }
            Thread.sleep(50);
        }
        assertTrue(port > 0, "the jar must report the port it listens on: " + Files.readString(output));
        HttpClient client = HttpClient.newHttpClient();
        for (String path : List.of("/health", "/ready", "/info")) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
        }
        running.destroy(); // SIGTERM
        assertTrue(running.waitFor(20, TimeUnit.SECONDS), "the JVM must exit after SIGTERM");
        String all = Files.readString(output);
        Files.delete(output);
        assertTrue(all.contains("\"event\":\"SHUTDOWN_STARTED\"") && all.contains("\"event\":\"SHUTDOWN_COMPLETED\""), all);
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            assertEquals(143, running.exitValue(), "128 + SIGTERM(15)");
        }
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
