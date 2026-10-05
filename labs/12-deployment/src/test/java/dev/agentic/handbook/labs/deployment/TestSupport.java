package dev.agentic.handbook.labs.deployment;

import dev.agentic.handbook.labs.security.AssistantModel;
import dev.agentic.handbook.labs.security.Authorizer;
import dev.agentic.handbook.labs.security.PolicyAuthorizer;
import dev.agentic.handbook.labs.security.SimulatedModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared helpers: configuration from pairs, a started app on a free loopback port, and plain HTTP calls. */
final class TestSupport {

    static final BuildInfo BUILD = new BuildInfo("lab-12-deployment", "0.1.0-test", "build-test");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    static final String INJECTION_REQUEST = "Summarize incident INC-1002 on notifications.";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private TestSupport() {
    }

    record Reply(int status, String body) {
    }

    /** A started application with its log lines collected. */
    record Running(DeploymentApp app, List<String> log) {
        String url(String path) {
            return "http://127.0.0.1:" + app.port() + path;
        }

        String logText() {
            synchronized (log) {
                return String.join("\n", log);
            }
        }
    }

    static Map<String, String> env(String... pairs) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("APP_ENV", "test");
        env.put("APP_PORT", "0");
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    static DeploymentConfig config(String... pairs) {
        return DeploymentConfig.fromEnvironment(env(pairs));
    }

    static Running start(String... pairs) throws IOException {
        return start(new SimulatedModel(), PolicyAuthorizer.helioPolicy(), pairs);
    }

    static Running start(AssistantModel model, Authorizer authorizer, String... pairs) throws IOException {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        DeploymentApp app = DeploymentApp.start(config(pairs), BUILD, log::add, CLOCK, model, authorizer);
        return new Running(app, log);
    }

    static Reply get(Running running, String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(running.url(path))).GET().build());
    }

    static Reply assist(Running running, String principal, String request) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(running.url("/assist")))
                .POST(HttpRequest.BodyPublishers.ofString(request));
        if (principal != null) {
            builder.header("X-Principal", principal);
        }
        return send(builder.build());
    }

    static Reply send(HttpRequest request) throws Exception {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    static boolean contains(String text, String... needles) {
        return Arrays.stream(needles).allMatch(text::contains);
    }
}
