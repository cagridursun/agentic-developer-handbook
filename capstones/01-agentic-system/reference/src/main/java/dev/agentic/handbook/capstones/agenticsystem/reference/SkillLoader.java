package dev.agentic.handbook.capstones.agenticsystem.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Skills decision of this reference: the incident-handoff procedure is
 * reusable and reviewable, so it lives in one Agent Skills SKILL.md instead of
 * being copied into prompts. This loader reads the lab subset of the format
 * (Lab 06 style); it is not a full specification validator.
 */
public final class SkillLoader {

    /** A loaded skill: name, description, and the instruction body. */
    public record Skill(String name, String description, String instructions) {
    }

    private SkillLoader() {
    }

    public static Skill loadIncidentHandoff() {
        String path = "/skills/incident-handoff/SKILL.md";
        try (InputStream stream = SkillLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing skill: " + path);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read skill: " + path, e);
        }
    }

    static Skill parse(String content) {
        List<String> lines = content.lines().toList();
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            throw new IllegalStateException("SKILL.md must start with '---' frontmatter.");
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            throw new IllegalStateException("SKILL.md frontmatter is never closed.");
        }
        String name = null;
        String description = null;
        for (String line : lines.subList(1, end)) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (key.equals("name")) {
                name = value;
            } else if (key.equals("description")) {
                description = value;
            }
        }
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalStateException("SKILL.md needs non-blank 'name' and 'description'.");
        }
        String body = String.join("\n", lines.subList(end + 1, lines.size())).strip();
        if (body.isBlank()) {
            throw new IllegalStateException("SKILL.md has no instruction body.");
        }
        return new Skill(name, description, body);
    }
}
