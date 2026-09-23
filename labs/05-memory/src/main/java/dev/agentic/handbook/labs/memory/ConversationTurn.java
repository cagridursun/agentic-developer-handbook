package dev.agentic.handbook.labs.memory;

/**
 * One line of the transcript. A list of these is conversation history: a
 * record of what happened. History is not memory — memory is what the
 * application deliberately keeps.
 */
public record ConversationTurn(
        Role role,
        String content) {
}
