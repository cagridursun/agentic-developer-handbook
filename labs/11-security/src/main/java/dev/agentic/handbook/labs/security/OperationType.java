package dev.agentic.handbook.labs.security;

/** What an operation does to the world, decided by the application's code, never by a description. */
public enum OperationType {
    /** Changes nothing. */
    READ,
    /** Changes the state of a system. Always needs an approval in this lab. */
    STATE_CHANGING,
    /** Approving a held operation. */
    GRANT_APPROVAL
}
