package dev.agentic.handbook.labs.observability;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A small, explicit redactor for secret-like data, applied to everything that
 * enters a span or a log line: tool arguments, tool results, and error messages.
 *
 * <p>"Observable" does not mean "record every byte". This class does two
 * things: it removes values that look like credentials, and it shortens
 * long values so a summary is never a payload dump.
 *
 * <p><b>This is not the security milestone.</b> It is a heuristic over known
 * shapes (an {@code Authorization} header, a bearer token, {@code password=...},
 * an {@code api_key} field, a Google API key). It will miss a secret in a shape
 * it does not know, and it cannot tell sensitive personal data from harmless
 * text. Deciding what may be recorded at all, who may read it, and how
 * hostile content is handled is Milestone 11.
 */
public final class Redactor {

    public static final String REDACTED = "[REDACTED]";

    /** Longest value kept in a span or log line. */
    static final int MAX_LENGTH = 200;

    // A key whose name says its value is a secret. "input_tokens" must not match: that is a count.
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(api[_-]?key|secret|password|passwd|authorization|credential|cookie|access[_-]?token"
                    + "|(^|[_.-])token$)");

    // "Authorization: Bearer abc", "authorization=Basic abc": the whole header value goes.
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?i)\\b(authorization)(\\s*[:=]\\s*)[^\\r\\n,;]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer)\\s+[A-Za-z0-9._~+/=-]+");
    // "password=hunter2", "api_key: abc", "client_secret=\"abc\"", "access_token=abc"
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)\\b([\\w.-]*(?:api[_-]?key|secret|password|passwd|token))(\\s*[=:]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,;&\"']+)");
    // "...?key=AIza..." in a URL echoed back by a provider error
    private static final Pattern QUERY_KEY = Pattern.compile("(?i)([?&]key=)[^&\\s]+");
    // The shape of a Google API key.
    private static final Pattern GOOGLE_KEY = Pattern.compile("\\bAIza[0-9A-Za-z_-]{30,}\\b");

    /** Redacts a value stored under a key: the whole value if the key names a secret, else its text. */
    public String value(String key, String value) {
        if (value == null) {
            return null;
        }
        if (SECRET_KEY.matcher(key).find()) {
            return REDACTED;
        }
        return text(value);
    }

    /** Redacts secret-like substrings of free text, then shortens it. */
    public String text(String text) {
        if (text == null) {
            return null;
        }
        String result = AUTHORIZATION_HEADER.matcher(text).replaceAll("$1$2" + REDACTED);
        result = BEARER.matcher(result).replaceAll("$1 " + REDACTED);
        result = ASSIGNMENT.matcher(result).replaceAll("$1$2" + REDACTED);
        result = QUERY_KEY.matcher(result).replaceAll("$1" + REDACTED);
        result = GOOGLE_KEY.matcher(result).replaceAll(REDACTED);
        return shorten(result);
    }

    /**
     * A safe, one-line summary of arguments or a result: {@code key=value} pairs
     * in the map's order, secret-named keys redacted, every value shortened. It
     * is a summary, not a serialization.
     */
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
