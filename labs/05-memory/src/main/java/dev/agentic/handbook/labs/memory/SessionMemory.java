package dev.agentic.handbook.labs.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Application-controlled state for one session, in one process.
 *
 * <p>The application decides what goes in, what gets updated, and what is
 * forgotten. Nothing here persists past the JVM: memory as a concept is
 * independent of storage technology, and this lab teaches the semantics
 * before any persistence choice.
 *
 * <p>This is honestly named: it is session memory, not a memory framework.
 * There is no interface because there is one implementation.
 */
public final class SessionMemory {

    /** LinkedHashMap keeps insertion order, so snapshots are deterministic. */
    private final LinkedHashMap<String, String> entries = new LinkedHashMap<>();

    /**
     * Stores or updates one entry. Remembering an existing key replaces its
     * value: memory holds the current state, not the history of states.
     */
    public void remember(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Memory key must not be blank.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Memory value must not be blank.");
        }
        entries.put(key.trim(), value.trim());
    }

    /**
     * Removes one entry. A memory system that can remember but cannot forget
     * is incomplete: preferences change, facts go stale, and users may ask
     * for deletion.
     */
    public void forget(String key) {
        if (key != null) {
            entries.remove(key.trim());
        }
    }

    /** Reads one entry, if present. */
    public Optional<String> get(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(entries.get(key.trim()));
    }

    /** An unmodifiable, insertion-ordered view of everything remembered. */
    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
}
