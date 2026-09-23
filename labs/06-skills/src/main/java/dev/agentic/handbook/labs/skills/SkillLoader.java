package dev.agentic.handbook.labs.skills;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads skills that follow the Agent Skills format (agentskills.io): a
 * directory named after the skill containing a SKILL.md with YAML frontmatter
 * (required: name, description) followed by Markdown instructions.
 *
 * <p>This loader is deliberately narrow. It reads the two required fields
 * used by this lab and validates the spec's naming rules; it is NOT a full
 * specification validator and does not parse general YAML. The official
 * project provides reference tooling ({@code skills-ref validate}) for real
 * validation.
 */
public final class SkillLoader {

    /**
     * The skills bundled with this lab. A static manifest, not a registry:
     * classpath directories cannot be listed reliably, and this lab teaches
     * the skill concept, not skill discovery.
     */
    public static final List<String> AVAILABLE_SKILLS =
            List.of("incident-handoff", "release-summary");

    /** Spec: lowercase alphanumerics and single hyphens, no edge hyphens. */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private SkillLoader() {
    }

    /** Progressive disclosure stage 1: metadata only, no instruction body. */
    public static SkillMetadata metadata(String skillName) {
        Skill skill = load(skillName);
        return new SkillMetadata(skill.name(), skill.description());
    }

    /** Progressive disclosure stage 2: the full skill, instructions included. */
    public static Skill load(String skillName) {
        String path = "/skills/" + skillName + "/SKILL.md";
        try (InputStream stream = SkillLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("No such skill on classpath: " + path);
            }
            return parse(skillName, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read skill: " + path, e);
        }
    }

    /**
     * Parses the lab's subset of the SKILL.md format and enforces the spec
     * rules this lab depends on: frontmatter delimited by {@code ---} lines,
     * a valid {@code name} matching the directory, a non-empty
     * {@code description} of at most 1024 characters, and a non-empty body.
     */
    static Skill parse(String directoryName, String content) {
        List<String> lines = content.lines().toList();
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            throw new IllegalStateException(
                    "SKILL.md must start with '---' YAML frontmatter.");
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            throw new IllegalStateException("SKILL.md frontmatter is never closed with '---'.");
        }

        String name = null;
        StringBuilder description = null;
        String currentKey = null;
        for (String line : lines.subList(1, end)) {
            if (line.startsWith(" ") && currentKey != null && description != null) {
                // Continuation line of a wrapped description value.
                description.append(' ').append(line.strip());
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            currentKey = key;
            if (key.equals("name")) {
                name = value;
            } else if (key.equals("description")) {
                description = new StringBuilder(value);
            }
        }

        if (name == null || name.isBlank()) {
            throw new IllegalStateException("SKILL.md frontmatter is missing 'name'.");
        }
        if (name.length() > 64 || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalStateException(
                    "Skill name '" + name + "' violates the Agent Skills naming rules.");
        }
        if (!name.equals(directoryName)) {
            throw new IllegalStateException(
                    "Skill name '" + name + "' must match its directory '" + directoryName + "'.");
        }
        String descriptionText = description == null ? "" : description.toString().strip();
        if (descriptionText.isBlank()) {
            throw new IllegalStateException("SKILL.md frontmatter is missing 'description'.");
        }
        if (descriptionText.length() > 1024) {
            throw new IllegalStateException("Skill description exceeds 1024 characters.");
        }

        List<String> bodyLines = new ArrayList<>(lines.subList(end + 1, lines.size()));
        String body = String.join("\n", bodyLines).strip();
        if (body.isBlank()) {
            throw new IllegalStateException("SKILL.md has no instruction body.");
        }

        return new Skill(name, descriptionText, body);
    }
}
