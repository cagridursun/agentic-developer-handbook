package dev.agentic.handbook.labs.deployment;

import static dev.agentic.handbook.labs.deployment.TestSupport.assist;
import static dev.agentic.handbook.labs.deployment.TestSupport.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentic.handbook.labs.deployment.TestSupport.Reply;
import dev.agentic.handbook.labs.deployment.TestSupport.Running;
import dev.agentic.handbook.labs.security.AssistantModel;
import dev.agentic.handbook.labs.security.AuthorizationDecision;
import dev.agentic.handbook.labs.security.AuthorizationDecision.Effect;
import dev.agentic.handbook.labs.security.Authorizer;
import dev.agentic.handbook.labs.security.PolicyAuthorizer;
import dev.agentic.handbook.labs.security.SimulatedModel;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    /** A model that holds a run open until the test lets it go: work that is in flight during shutdown. */
    private static final class HeldModel implements AssistantModel {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final SimulatedModel delegate = new SimulatedModel();

        @Override
        public dev.agentic.handbook.labs.security.ModelStep next(String request,
                List<dev.agentic.handbook.labs.security.Exchange> history) {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return delegate.next(request, history);
        }
    }

    private static List<String> events(Running running) {
        List<String> names = new ArrayList<>();
        synchronized (running.log()) {
            for (String line : running.log()) {
                names.add(line.split("\"event\":\"")[1].split("\"")[0]);
            }
        }
        return names;
    }

    @Test
    void startupIsRecordedWithTheArtifactIdentity() throws Exception {
        Running running = TestSupport.start("APP_ENV", "demo");
        try {
            assertEquals(List.of("STARTUP_BEGIN", "READINESS_CHANGED", "STARTUP_COMPLETED"), events(running));
            String line = running.log().get(2);
            assertTrue(TestSupport.contains(line, "\"ts\":\"2026-01-01T00:00:00Z\"", "\"version\":\"0.1.0-test\"",
                    "\"build\":\"build-test\"", "\"env\":\"demo\"", "\"app\":\"lab-12-deployment\""), line);
        } finally {
            running.app().shutdown();
        }
    }

    @Test
    void gracefulShutdownFlipsReadinessFinishesInFlightWorkAndRecordsIt() throws Exception {
        HeldModel model = new HeldModel();
        Running running = TestSupport.start(model, PolicyAuthorizer.helioPolicy(), "APP_SHUTDOWN_GRACE_SECONDS", "10");
        CompletableFuture<Reply> inFlight = CompletableFuture.supplyAsync(() -> {
            try {
                return assist(running, "triage-assistant", TestSupport.INJECTION_REQUEST);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(model.started.await(10, TimeUnit.SECONDS), "the run must be in flight before shutdown starts");

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(running.app()::shutdown);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (running.app().state() != State.DRAINING && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(State.DRAINING, running.app().state());

        // Draining: still alive, no longer ready, and new work is refused.
        assertEquals(200, get(running, "/health").status());
        Reply notReady = get(running, "/ready");
        assertEquals(503, notReady.status());
        assertTrue(notReady.body().contains("\"status\":\"NOT_READY\"") && notReady.body().contains("DRAINING"), notReady.body());
        Reply refused = assist(running, "triage-assistant", TestSupport.INJECTION_REQUEST);
        assertEquals(503, refused.status());
        assertFalse(shutdown.isDone(), "shutdown must wait for the run that is in flight");

        model.release.countDown();
        Reply finished = inFlight.get(10, TimeUnit.SECONDS);
        assertEquals(200, finished.status(), "the in-flight run completes normally");
        assertTrue(finished.body().contains("\"outcome\":\"DENIED\""), "and it was still authorized, not waved through");
        shutdown.get(10, TimeUnit.SECONDS);

        assertEquals(State.STOPPED, running.app().state());
        List<String> events = events(running);
        assertTrue(events.indexOf("READINESS_CHANGED") < events.indexOf("SHUTDOWN_STARTED"), events.toString());
        assertTrue(events.indexOf("RUN_COMPLETED") < events.indexOf("SHUTDOWN_COMPLETED"),
                "the run finished before shutdown completed: " + events);
        assertTrue(running.logText().contains("\"drained\":\"true\""));
        assertFalse(events.contains("SHUTDOWN_TIMEOUT"));
    }

    @Test
    void shutdownThatCannotWaitLongEnoughSaysSoInsteadOfPretending() throws Exception {
        HeldModel model = new HeldModel();
        Running running = TestSupport.start(model, PolicyAuthorizer.helioPolicy(), "APP_SHUTDOWN_GRACE_SECONDS", "0");
        CompletableFuture<Void> stuck = CompletableFuture.runAsync(() -> {
            try {
                assist(running, "triage-assistant", TestSupport.INJECTION_REQUEST);
            } catch (Exception expected) {
                // The connection is closed under the run when the grace period is 0.
            }
        });
        assertTrue(model.started.await(10, TimeUnit.SECONDS));
        running.app().shutdown();
        assertTrue(running.logText().contains("\"event\":\"SHUTDOWN_TIMEOUT\"") && running.logText().contains("\"abandoned\":\"1\""),
                running.logText());
        assertTrue(running.logText().contains("\"drained\":\"false\""));
        model.release.countDown();
        stuck.get(10, TimeUnit.SECONDS);
    }

    @Test
    void shutdownTwiceIsSafe() throws Exception {
        Running running = TestSupport.start();
        running.app().shutdown();
        running.app().shutdown();
        assertEquals(1, events(running).stream().filter("SHUTDOWN_COMPLETED"::equals).count());
        assertThrows(Exception.class, () -> get(running, "/health"), "a stopped application accepts nothing");
    }

    @Test
    void anAuthorizerThatAllowsEverythingIsRefusedAtStartup() {
        Authorizer allowAll = request -> new AuthorizationDecision(Effect.ALLOW, "ALLOWED");
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DeploymentApp.start(
                TestSupport.config(), TestSupport.BUILD, log::add, TestSupport.CLOCK, new SimulatedModel(), allowAll));
        assertTrue(e.getMessage().contains("self-check"));
        assertTrue(String.join("\n", log).contains("STARTUP_REFUSED"));
        assertFalse(String.join("\n", log).contains("STARTUP_COMPLETED"));
    }

    @Test
    void anAuthorizerThatSkipsTheApprovalRuleIsRefusedToo() {
        Authorizer noApproval = request -> request.principal().isPresent()
                && request.principal().get().id().equals("incident-responder")
                ? new AuthorizationDecision(Effect.ALLOW, "ALLOWED") : new AuthorizationDecision(Effect.DENY, "NO");
        assertThrows(IllegalStateException.class, () -> DeploymentApp.start(TestSupport.config(), TestSupport.BUILD,
                line -> { }, TestSupport.CLOCK, new SimulatedModel(), noApproval));
    }

    @Test
    void anAuthorizerThatThrowsIsRefusedAtStartup() {
        Authorizer broken = request -> {
            throw new IllegalStateException("policy store unavailable");
        };
        assertThrows(IllegalStateException.class, () -> DeploymentApp.start(TestSupport.config(), TestSupport.BUILD,
                line -> { }, TestSupport.CLOCK, new SimulatedModel(), broken));
    }

    @Test
    void aVersionMismatchStopsStartupBeforeAnythingListens() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        assertThrows(ConfigException.class, () -> DeploymentApp.start(TestSupport.config("APP_EXPECTED_VERSION", "9.9.9"),
                TestSupport.BUILD, log::add));
        assertTrue(log.isEmpty(), "nothing was started, so nothing was logged");
    }

    @Test
    void aPortThatIsAlreadyTakenFailsStartup() throws IOException {
        try (ServerSocket taken = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            assertThrows(IOException.class, () -> DeploymentApp.start(
                    TestSupport.config("APP_PORT", String.valueOf(taken.getLocalPort())), TestSupport.BUILD, line -> { }));
        }
    }
}
