package dev.agentic.handbook.labs.deployment;

import dev.agentic.handbook.labs.security.HelioPlatform;
import dev.agentic.handbook.labs.security.HelioTools;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The settings that change between environments, read from environment
 * variables and checked before anything starts. A deployment changes these and
 * never the source.
 *
 * <p>What configuration may and may not do:
 * <ul>
 *   <li>It may choose a port, an address, a log level, a name for the
 *       environment, which of the application's own tools are offered, and a
 *       grace period for shutdown.</li>
 *   <li>It may <b>not</b> add a tool, change a policy, switch authorization,
 *       approval, or validation off, or change what the artifact says it is.
 *       There is no setting for any of those, and an unknown {@code APP_*}
 *       setting is an error, so a hopeful {@code APP_AUTHORIZATION=off} stops
 *       the application instead of being ignored.</li>
 *   <li>It carries at most one secret, {@code APP_MONITORING_TOKEN}, supplied at
 *       runtime. The token is never printed, logged, or returned.</li>
 * </ul>
 *
 * <p>Every problem is reported at once, then the application does not start.
 * Error messages name settings and rules, never values.
 */
public record DeploymentConfig(
        String environment,
        String bindAddress,
        int port,
        LogLevel logLevel,
        Set<String> enabledTools,
        Optional<String> expectedVersion,
        int shutdownGraceSeconds,
        Optional<String> monitoringToken) {

    public static final String ENV = "APP_ENV";
    public static final String PORT = "APP_PORT";
    public static final String BIND_ADDRESS = "APP_BIND_ADDRESS";
    public static final String LOG_LEVEL = "APP_LOG_LEVEL";
    public static final String MODE = "APP_MODE";
    public static final String TOOLS = "APP_TOOLS";
    public static final String EXPECTED_VERSION = "APP_EXPECTED_VERSION";
    public static final String SHUTDOWN_GRACE_SECONDS = "APP_SHUTDOWN_GRACE_SECONDS";
    public static final String MONITORING_TOKEN = "APP_MONITORING_TOKEN";

    private static final Set<String> KNOWN = Set.of(ENV, PORT, BIND_ADDRESS, LOG_LEVEL, MODE, TOOLS,
            EXPECTED_VERSION, SHUTDOWN_GRACE_SECONDS, MONITORING_TOKEN);
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,19}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    /** The tools the application registers. Configuration can only choose among them. */
    public static List<String> registeredTools() {
        return List.copyOf(HelioTools.definitions(new HelioPlatform()).keySet());
    }

    /** Reads and validates the settings. Throws {@link ConfigException} listing every problem. */
    public static DeploymentConfig fromEnvironment(Map<String, String> env) {
        List<String> problems = new ArrayList<>();

        env.keySet().stream().filter(key -> key.startsWith("APP_") && !KNOWN.contains(key)).sorted()
                .forEach(key -> problems.add("unknown setting " + key
                        + " (settings cannot change authorization, approval, or validation; the application has none for them)"));

        String environment = env.get(ENV);
        if (environment == null || environment.isBlank()) {
            problems.add(ENV + " is required (for example local, test, or demo)");
        } else if (!NAME.matcher(environment).matches()) {
            problems.add(ENV + " must be 1 to 20 characters: lowercase letters, digits, and hyphens, starting with a letter");
        }

        int port = 8080;
        if (env.containsKey(PORT)) {
            port = integer(env.get(PORT), 0, 65535).orElse(-1);
            if (port < 0) {
                problems.add(PORT + " must be an integer from 0 to 65535 (0 picks a free port, for tests)");
            }
        }

        String bindAddress = env.getOrDefault(BIND_ADDRESS, "127.0.0.1");
        try {
            InetAddress.ofLiteral(bindAddress);
        } catch (IllegalArgumentException e) {
            problems.add(BIND_ADDRESS + " must be an IP address literal such as 127.0.0.1 or 0.0.0.0");
        }

        LogLevel logLevel = LogLevel.INFO;
        if (env.containsKey(LOG_LEVEL)) {
            try {
                logLevel = LogLevel.valueOf(env.get(LOG_LEVEL));
            } catch (IllegalArgumentException e) {
                problems.add(LOG_LEVEL + " must be one of DEBUG, INFO, WARN, ERROR");
            }
        }

        String mode = env.getOrDefault(MODE, "deterministic");
        if (!mode.equals("deterministic")) {
            problems.add(MODE + " must be deterministic; no other mode is implemented in this lab");
        }

        Set<String> tools = new LinkedHashSet<>(registeredTools());
        if (env.containsKey(TOOLS)) {
            Set<String> requested = new LinkedHashSet<>(Arrays.asList(env.get(TOOLS).split(",", -1)));
            if (!registeredTools().containsAll(requested)) {
                problems.add(TOOLS + " may only list registered tools: " + String.join(", ", registeredTools()));
            } else {
                tools = requested;
            }
        }

        Optional<String> expectedVersion = Optional.ofNullable(env.get(EXPECTED_VERSION));
        if (expectedVersion.isPresent() && !VERSION.matcher(expectedVersion.get()).matches()) {
            problems.add(EXPECTED_VERSION + " must look like a version, for example 0.1.0-SNAPSHOT");
        }

        int grace = 10;
        if (env.containsKey(SHUTDOWN_GRACE_SECONDS)) {
            grace = integer(env.get(SHUTDOWN_GRACE_SECONDS), 0, 60).orElse(-1);
            if (grace < 0) {
                problems.add(SHUTDOWN_GRACE_SECONDS + " must be an integer from 0 to 60");
            }
        }

        Optional<String> token = Optional.ofNullable(env.get(MONITORING_TOKEN));
        if (token.isPresent() && token.get().isBlank()) {
            problems.add(MONITORING_TOKEN + " is set but empty; supply a value at runtime or leave it unset");
        }

        if (!problems.isEmpty()) {
            throw new ConfigException("invalid configuration: " + String.join("; ", problems));
        }
        return new DeploymentConfig(environment, bindAddress, port, logLevel, Collections.unmodifiableSet(new LinkedHashSet<>(tools)),
                expectedVersion, grace, token);
    }

    /** Refuses to run an artifact that is not the one the deployment expects. */
    public void checkAgainst(BuildInfo build) {
        expectedVersion.filter(expected -> !expected.equals(build.version())).ifPresent(expected -> {
            throw new ConfigException(EXPECTED_VERSION + " does not match the artifact version "
                    + build.version() + "; this is not the artifact the deployment expects");
        });
    }

    /** The settings in effect, with the secret hidden. Safe to print and to log. */
    public String describe() {
        return String.join("\n",
                "environment=" + environment,
                "bindAddress=" + bindAddress,
                "port=" + port,
                "logLevel=" + logLevel,
                "mode=deterministic",
                "tools=" + String.join(",", enabledTools),
                "expectedVersion=" + expectedVersion.orElse("(not set)"),
                "shutdownGraceSeconds=" + shutdownGraceSeconds,
                "monitoringToken=" + (monitoringToken.isPresent() ? "set (hidden)" : "not set"));
    }

    /** The generated record string would print the token. */
    @Override
    public String toString() {
        return describe().replace('\n', ' ');
    }

    private static Optional<Integer> integer(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            return value >= min && value <= max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
