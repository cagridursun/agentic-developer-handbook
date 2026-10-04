package dev.agentic.handbook.labs.security.vulnerable;

import dev.agentic.handbook.labs.security.HelioPlatform;
import java.io.PrintStream;

/**
 * <b>INTENTIONALLY VULNERABLE. Educational and simulated. In memory only.</b>
 *
 * <p>Opt-in only: it is not the default run. Start it with
 * {@code ./mvnw -pl labs/11-security compile exec:java -Dexec.mainClass=dev.agentic.handbook.labs.security.vulnerable.VulnerableDemo}.
 * It changes nothing outside its own in-memory platform object.
 */
public final class VulnerableDemo {

    private VulnerableDemo() {
    }

    public static void main(String[] args) {
        run(System.out);
    }

    public static void run(PrintStream out) {
        HelioPlatform platform = new HelioPlatform();
        out.println("SIMULATED, INTENTIONALLY UNSAFE ASSISTANT (educational, in memory, no real system)");
        out.println("Request: \"Summarize incident INC-1002 on notifications.\"");
        out.println("No identity, no validation, no authorization, no approval: whatever the model proposes, runs.");
        out.println();
        VulnerableAssistant.Run run = new VulnerableAssistant(platform).run("Summarize incident INC-1002 on notifications.");
        out.println("Calls the application ran, on the model's say-so:");
        run.executedCalls().forEach(call -> out.println("  " + call));
        out.println();
        out.println("State changes that happened on the platform:");
        platform.stateChanges().forEach(change -> out.println("  " + change));
        out.println();
        out.println("The answer the user received:");
        out.println(run.answer());
        out.println();
        out.println("What went wrong: the retrieved note told the model to restart billing, and the application");
        out.println("did it. The raw status record, with its token, went straight into the answer.");
        out.println("None of that needed the model to be 'broken'; it only needed the application to trust it.");
    }
}
