package dev.agentic.handbook.labs.toolcalling;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tool implementation: deterministic, in-memory, boring Java.
 *
 * <p>This class is the application capability behind the {@code getServiceStatus}
 * tool declaration. It has no model dependency and could be called from a unit
 * test, a REST controller, or a CLI exactly as easily as from this lab.
 */
public final class ServiceCatalog {

    private static final Map<String, ServiceStatus> SERVICES = Map.of(
            "authorization", new ServiceStatus(
                    "authorization", ServiceState.HEALTHY, "All checks are passing."),
            "notifications", new ServiceStatus(
                    "notifications", ServiceState.DEGRADED, "Email delivery is delayed by up to 10 minutes."),
            "billing", new ServiceStatus(
                    "billing", ServiceState.HEALTHY, "All checks are passing."),
            "search", new ServiceStatus(
                    "search", ServiceState.MAINTENANCE, "Planned index rebuild until 14:00 UTC."));

    private ServiceCatalog() {
    }

    /** Looks up a service by name. Case and surrounding whitespace are ignored. */
    public static Optional<ServiceStatus> getServiceStatus(String serviceName) {
        if (serviceName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SERVICES.get(serviceName.trim().toLowerCase(Locale.ROOT)));
    }

    /** The service names this catalog knows, for helpful error messages. */
    public static Set<String> knownServices() {
        return SERVICES.keySet();
    }
}
