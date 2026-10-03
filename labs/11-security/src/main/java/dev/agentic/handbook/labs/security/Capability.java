package dev.agentic.handbook.labs.security;

/** What a principal may be granted. Each tool needs exactly one capability. */
public enum Capability {
    READ_SERVICE_STATUS,
    READ_INCIDENT_NOTES,
    READ_DEPLOYMENTS,
    RESTART_SERVICE,
    ROLLBACK_DEPLOYMENT,
    MODIFY_DEPLOYMENT_CACHE,
    APPROVE_OPERATIONS
}
