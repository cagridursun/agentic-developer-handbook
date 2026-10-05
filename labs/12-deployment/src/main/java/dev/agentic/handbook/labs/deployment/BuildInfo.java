package dev.agentic.handbook.labs.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Which artifact is running. The values are written into the jar when it is
 * built ({@code deployment.properties}, filtered by Maven), so they describe the
 * artifact and cannot be changed by runtime configuration.
 */
public record BuildInfo(String app, String version, String buildId) {

    /** Reads the identity packaged inside this artifact. */
    public static BuildInfo load() {
        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/deployment.properties")) {
            if (in == null) {
                throw new IllegalStateException("deployment.properties is missing from the artifact.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("deployment.properties could not be read.", e);
        }
        return new BuildInfo(properties.getProperty("app.name"), properties.getProperty("app.version"),
                properties.getProperty("build.id"));
    }
}
