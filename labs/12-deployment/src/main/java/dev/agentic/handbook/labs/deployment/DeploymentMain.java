package dev.agentic.handbook.labs.deployment;

import java.io.IOException;
import java.util.Arrays;

/**
 * The entry point of the artifact. It reads configuration from the environment,
 * refuses to start on a problem, and otherwise runs until it receives SIGTERM or
 * SIGINT, then shuts down gracefully.
 *
 * <p>{@code --check-config} validates the configuration, prints it with the secret
 * hidden, and exits without starting anything.
 *
 * <p>Exit codes: 0 for a successful check, 2 for invalid configuration, 1 for a
 * failure to start. After a signal the JVM exits with 128 plus the signal number.
 */
public final class DeploymentMain {

    private DeploymentMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        BuildInfo build = BuildInfo.load();
        DeploymentConfig config;
        try {
            config = DeploymentConfig.fromEnvironment(System.getenv());
            config.checkAgainst(build);
        } catch (ConfigException e) {
            System.err.println("CONFIGURATION ERROR: " + e.getMessage());
            System.exit(2);
            return;
        }
        if (Arrays.asList(args).contains("--check-config")) {
            System.out.println("app=" + build.app() + "\nversion=" + build.version() + "\nbuild=" + build.buildId());
            System.out.println(config.describe());
            System.out.println("configuration is valid");
            return;
        }
        DeploymentApp app;
        try {
            app = DeploymentApp.start(config, build, System.out::println);
        } catch (IOException | IllegalStateException e) {
            System.err.println("STARTUP FAILED: " + e.getClass().getSimpleName());
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown, "graceful-shutdown"));
        app.awaitStopped();
    }
}
