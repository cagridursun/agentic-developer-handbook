package dev.agentic.handbook.labs.deployment;

/** How much the application says. Ordered from most to least detailed. */
public enum LogLevel {
    DEBUG, INFO, WARN, ERROR;

    boolean enabled(LogLevel configured) {
        return compareTo(configured) >= 0;
    }
}
