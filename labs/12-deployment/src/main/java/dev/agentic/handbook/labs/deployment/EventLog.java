package dev.agentic.handbook.labs.deployment;

import dev.agentic.handbook.labs.security.Redactor;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The deployment's structured log: one JSON object per line, written to a sink
 * (standard output in the container, where the platform collects it).
 *
 * <p>Every line carries the same minimal identity: the application, the version
 * and build id of the artifact, and the environment name. Every value goes
 * through the Lab 11 {@link Redactor} first, so a registered secret cannot appear
 * in a line, and the log never receives a request body, a credential, or an
 * environment dump, because no caller is given a way to pass one.
 */
final class EventLog {

    private final BuildInfo build;
    private final String environment;
    private final LogLevel configured;
    private final Redactor redactor;
    private final Clock clock;
    private final Consumer<String> sink;

    EventLog(BuildInfo build, String environment, LogLevel configured, Redactor redactor, Clock clock,
            Consumer<String> sink) {
        this.build = build;
        this.environment = environment;
        this.configured = configured;
        this.redactor = redactor;
        this.clock = clock;
        this.sink = sink;
    }

    /** @param fields alternating keys and values */
    synchronized void log(LogLevel level, String event, String... fields) {
        if (!level.enabled(configured)) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", clock.instant().toString());
        line.put("level", level.name());
        line.put("event", event);
        line.put("app", build.app());
        line.put("version", build.version());
        line.put("build", build.buildId());
        line.put("env", environment);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            if (!fields[i + 1].isEmpty()) {
                line.put(fields[i], redactor.value(fields[i], redactor.redactSecrets(fields[i + 1])));
            }
        }
        sink.accept(Json.write(line));
    }
}
