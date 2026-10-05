package dev.agentic.handbook.labs.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Dockerfile is checked as text, because the tests must not need Docker. These checks prove the
 * file says what the lab teaches; whether an image builds is checked by {@code docker build} in CI.
 */
class DockerfileTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path DOCKERIGNORE = Path.of("..", "..", ".dockerignore");

    private static List<String> instructions() throws IOException {
        // Join line continuations, drop comments and blanks.
        String text = Files.readString(DOCKERFILE).replace("\\\n", " ");
        return text.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
    }

    private static List<String> starting(String instruction) throws IOException {
        return instructions().stream().filter(line -> line.startsWith(instruction + " ")).toList();
    }

    @Test
    void itIsTwoStagesAndTheRuntimeStageIsAJreNotTheJdk() throws IOException {
        List<String> from = starting("FROM");
        assertEquals(2, from.size(), from.toString());
        assertTrue(from.get(0).contains("-jdk") && from.get(0).endsWith("AS build"), from.get(0));
        assertTrue(from.get(1).contains("-jre"), from.get(1));
    }

    @Test
    void baseImagesAreTaggedForJava27AndNeverLatest() throws IOException {
        for (String from : starting("FROM")) {
            assertTrue(from.contains(":27-"), from);
            assertFalse(from.contains("latest"), from);
        }
    }

    @Test
    void theRuntimeStageCopiesOnlyTheBuiltArtifactFromTheBuildStage() throws IOException {
        List<String> copies = starting("COPY");
        List<String> runtime = copies.stream().filter(line -> line.startsWith("COPY --from=build")).toList();
        assertEquals(2, runtime.size(), copies.toString());
        assertTrue(runtime.get(0).contains("target/lab-12-deployment.jar"));
        assertTrue(runtime.get(1).contains("target/lib/"));
        assertEquals(1, copies.size() - runtime.size(), "the only other COPY is the sources into the build stage");
    }

    @Test
    void theApplicationRunsAsANonRootUser() throws IOException {
        List<String> users = starting("USER");
        assertEquals(1, users.size());
        String user = users.get(0).substring("USER ".length()).split(":")[0];
        assertFalse(user.equals("root") || user.equals("0"), users.get(0));
        assertTrue(user.matches("[0-9]{4,}|[a-z][a-z0-9_-]*"), users.get(0));
        // USER comes after the files are in place and before the entry point.
        List<String> all = instructions();
        assertTrue(all.indexOf(users.get(0)) < all.indexOf(starting("ENTRYPOINT").get(0)));
    }

    @Test
    void thePortIsExposedExplicitly() throws IOException {
        assertEquals(List.of("EXPOSE 8080"), starting("EXPOSE"));
    }

    @Test
    void theStartCommandIsExecFormSoTheJvmReceivesSignals() throws IOException {
        List<String> entrypoints = starting("ENTRYPOINT");
        assertEquals(1, entrypoints.size());
        String entrypoint = entrypoints.get(0);
        assertTrue(entrypoint.startsWith("ENTRYPOINT [\"java\""), entrypoint);
        assertTrue(entrypoint.endsWith("\"-jar\", \"/app/lab-12-deployment.jar\"]"), entrypoint);
        assertFalse(entrypoint.contains("sh") || entrypoint.contains("-c\""), "no shell in front of the JVM: " + entrypoint);
        assertTrue(starting("CMD").isEmpty());
    }

    @Test
    void theJvmIsConfiguredToUseTheContainersMemoryAndToExitOnOutOfMemory() throws IOException {
        String entrypoint = starting("ENTRYPOINT").get(0);
        assertTrue(entrypoint.contains("-XX:MaxRAMPercentage="));
        assertTrue(entrypoint.contains("-XX:+ExitOnOutOfMemoryError"));
        assertFalse(entrypoint.contains("--enable-preview"));
    }

    @Test
    void noSecretIsSetCopiedOrPassedAsABuildArgument() throws IOException {
        Pattern secretName = Pattern.compile("(?i)(secret|token|password|passwd|api[_-]?key|credential)");
        for (String line : instructions()) {
            if (line.startsWith("ENV ") || line.startsWith("ARG ")) {
                assertFalse(secretName.matcher(line).find(), line);
            }
        }
        String text = Files.readString(DOCKERFILE);
        assertFalse(text.contains("COPY .env") || text.contains(".pem") || text.contains(".key"));
        assertFalse(Pattern.compile("AIza[0-9A-Za-z_-]{30,}|sk-[A-Za-z0-9]{20,}").matcher(text).find());
    }

    @Test
    void theEnvironmentNameIsNotBakedIntoTheImage() throws IOException {
        for (String env : starting("ENV")) {
            assertFalse(env.contains("APP_ENV"), "a deployment must say which environment it is: " + env);
        }
    }

    @Test
    void theBuildContextExcludesVersionControlBuildOutputAndEnvironmentFiles() throws IOException {
        List<String> ignored = Files.readAllLines(DOCKERIGNORE);
        for (String entry : List.of(".git", "**/target", ".env", "**/*.env")) {
            assertTrue(ignored.contains(entry), entry);
        }
    }
}
