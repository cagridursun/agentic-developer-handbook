package dev.agentic.handbook.labs.deployment;

/**
 * The configuration is invalid, so the application must not start. The message
 * names settings and the rule they broke. It never contains a setting's value,
 * so a secret placed in the wrong setting is not copied into a log.
 */
public final class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }
}
