package dev.agentic.handbook.labs.security;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lab 10's redactor, extended with one thing: values the application knows are
 * secrets (here, a fake token) are removed wherever they appear in text, even
 * with no label in front of them.
 *
 * <p><b>It is still incomplete.</b> It removes (1) values of keys whose name says
 * "secret", (2) a few known credential shapes, and (3) the exact secret values
 * registered with it. A secret in any other shape (re-encoded, split, paraphrased,
 * or simply not registered) passes through, and personal data is not recognized
 * at all. Redaction is a safety net under the controls that keep sensitive data
 * out of tool results in the first place; it is not a data loss prevention system.
 */
public final class Redactor {

    public static final String REDACTED = "[REDACTED]";

    /** Longest value kept in a span: a summary, not a payload dump. */
    static final int MAX_LENGTH = 200;

    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(api[_-]?key|api[_-]?token|secret|password|passwd|authorization|credential|cookie"
                    + "|access[_-]?token|(^|[_.-])token$)");
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?i)\\b(authorization)(\\s*[:=]\\s*)[^\\r\\n,;]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)\\b([\\w.-]*(?:api[_-]?key|secret|password|passwd|token))(\\s*[=:]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,;&\"']+)");

    private final Set<String> knownSecrets;

    /** @param knownSecrets exact secret values this application holds, removed wherever they appear */
    public Redactor(Set<String> knownSecrets) {
        this.knownSecrets = Set.copyOf(knownSecrets);
    }

    /** True if a field of this name should never be recorded or returned. */
    public boolean isSensitiveKey(String key) {
        return key != null && SECRET_KEY.matcher(key).find();
    }

    /** Redacts a value stored under a key: all of it if the key names a secret, else its text. */
    public String value(String key, String value) {
        if (value == null) {
            return null;
        }
        return isSensitiveKey(key) ? REDACTED : text(value);
    }

    /** Redacts secret-like substrings, then shortens: for telemetry. */
    public String text(String text) {
        return text == null ? null : shorten(redactSecrets(text));
    }

    /** Redacts secret-like substrings without shortening: for text that must stay whole, such as a tool result. */
    public String redactSecrets(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (String secret : knownSecrets) {
            result = result.replace(secret, REDACTED);
        }
        result = AUTHORIZATION_HEADER.matcher(result).replaceAll("$1$2" + REDACTED);
        result = BEARER.matcher(result).replaceAll("$1 " + REDACTED);
        return ASSIGNMENT.matcher(result).replaceAll("$1$2" + REDACTED);
    }

    /** A one-line summary of arguments: {@code key=value} pairs, secret-named keys redacted. */
    public String describe(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + value(entry.getKey(), String.valueOf(entry.getValue())))
                .collect(Collectors.joining(", "));
    }

    static String shorten(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 3) + "...";
    }
}
