package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTemplatesTest {
    private static final List<String> NAMES = List.of("Steve", "Steve2", "Alice");

    private static Optional<ChatTemplates.TemplateMatch> match(String line, String... templates) {
        return ChatTemplates.match(line, List.of(templates), NAMES);
    }

    @Test
    void overlappingRulesWithDifferentBodyBoundariesAreRejectedInEitherOrder() {
        assertTrue(match("Steve: : hello", "{name}: {content}", "{name}: : {content}").isEmpty());
        assertTrue(match("Steve: : hello", "{name}: : {content}", "{name}: {content}").isEmpty());
    }

    @Test
    void literalBoundaryPreservesSpacesLineBreakAndRepeatedSenderInBody() {
        var result = match("Steve:  Steve\n😀  ", "{name}: {content}").orElseThrow();
        assertEquals(" Steve\n😀  ", result.content());
    }

    @Test
    void vanillaAngleTemplate() {
        var m = match("<Steve> hi", "<{name}> {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("Steve", m.get().displayLabel());
        assertEquals("hi", m.get().content());
    }

    @Test
    void longestNameWinsInsideAlternation() {
        var m = match("<Steve2> hi", "<{name}> {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve2", m.get().playerName());
    }

    @Test
    void prefixSuffixTemplate() {
        var m = match("[VIP] Steve » hello", "[{prefix}] {name} » {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("VIPSteve", m.get().displayLabel());
        assertEquals("hello", m.get().content());
    }

    @Test
    void displayNameTemplateResolvesContainedName() {
        var m = match("[Guest] Steve7: hi", "{display_name}: {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("[Guest] Steve7", m.get().displayLabel());
        assertEquals("hi", m.get().content());
    }

    @Test
    void suffixStyleContentIsSupported() {
        // e33chat limitation not repeated: {content} may come first.
        var m = match("hello < Steve", "{content} < {name}");
        assertTrue(m.isPresent());
        assertEquals("hello", m.get().content());
        assertEquals("Steve", m.get().playerName());
    }

    @Test
    void separatorPlaceholderSplitsCorrectly() {
        var m = match("Steve: > hi", "{name}{sep}> {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("hi", m.get().content());
    }

    @Test
    void variableSeparatorCannotConsumeBodyPunctuationOrLeadingSpaces() {
        assertTrue(match("Steve:  !hello", "{name}{sep}{content}").isEmpty());
        assertEquals(" !hello", match("Steve:  !hello", "{name}: {content}").orElseThrow().content());
    }

    @Test
    void colorCodesAreStrippedBeforeMatching() {
        var m = match("§6Steve§r: hi", "{name}: {content}");
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("hi", m.get().content());
    }

    @Test
    void unknownNameIsRejected() {
        assertTrue(match("<Bob> hi", "<{name}> {content}").isEmpty());
    }

    @Test
    void arbitraryProseIsRejected() {
        assertTrue(match("the server restarted < soon", "<{name}> {content}").isEmpty());
    }

    @Test
    void duplicateContentPlaceholderIsRejected() {
        assertTrue(match("hi", "{content} {content}").isEmpty());
    }

    @Test
    void duplicateNamePlaceholderIsRejected() {
        assertTrue(match("Steve Steve: hi", "{name} {name}: {content}").isEmpty());
    }

    @Test
    void templateWithoutContentIsRejected() {
        assertTrue(match("Steve: hi", "<{name}>").isEmpty());
    }

    @Test
    void emptyKnownNamesDisablesMatching() {
        assertTrue(ChatTemplates.match("<Steve> hi", List.of("<{name}> {content}"), List.of())
                .isEmpty());
    }

    @Test
    void whisperMatchSharesEngine() {
        var m = ChatTemplates.matchWhisper("[Steve -> me] hi",
                List.of("[{name} -> me] {content}"), NAMES);
        assertTrue(m.isPresent());
        assertEquals("Steve", m.get().playerName());
        assertEquals("hi", m.get().content());
    }
}
