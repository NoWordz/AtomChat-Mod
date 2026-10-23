package com.atom.chat.ui.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the screen's input wiring (D23a).
 *
 * <p><b>Why a source probe.</b> The gate and its own contract are unit-tested
 * in {@link InputGateTest}, but that test cannot see whether the screen ever
 * <em>puts handlers into</em> the gate. Nothing offline can: {@code AtomChatScreen}
 * needs the Minecraft client to load, the golden frames render page objects and
 * never touch the event layer, and the routing mechanics are shared-library
 * behaviour. A build with an empty chain therefore stays green while every
 * click, drag, scroll and keystroke falls straight through to {@code super} —
 * the input layer is dead. That exact defect shipped once in this block (a
 * second, separately-declared {@code InputRouter} field left the gate's own
 * router empty and orphaned the seven registrations), and nothing except
 * reading the diff caught it.
 *
 * <p>So the wiring is asserted where it is written. A second chain, a chain
 * hung off something other than the gate, or a reordered priority list is a
 * failure here on all three platforms.
 *
 * <p>The same reasoning as {@code PageNavStyleSourceTest}: the three screen
 * copies differ only in Minecraft API spellings, so one set of assertions runs
 * against all three and a one-platform fix is a failure.
 */
class InputWiringSourceTest {

    @Test
    void rawPressAndReleaseAreObservedBeforeTheAnimationGate() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            int click = src.indexOf("public boolean mouseClicked(");
            int clickGate = src.indexOf("gate.click(", click);
            int press = src.indexOf("foreignPointer.press(button,", click);
            assertTrue(press > click && press < clickGate,
                    screen + ": animation must not hide a raw press from Quark's mask");
            int release = src.indexOf("public boolean mouseReleased(");
            int releaseGate = src.indexOf("gate.release(", release);
            int observed = src.indexOf("foreignPointer.release(button)", release);
            assertTrue(observed > release && observed < releaseGate,
                    screen + ": animation must not hide the release that clears the mask");
        }
    }

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /** The gate field declaration this guard is anchored on. */
    private static final String GATE_DECLARATION = "private final InputGate gate = ";

    /**
     * The registration order, frozen: first registered wins. Flattened so the
     * assertion is on the sequence of calls, not on the formatting.
     */
    private static final String FROZEN_CHAIN_FLAT =
            ".add(newClosingStateInput())"
                    + ".add(foreignChatUiInput())"
                    + ".add(outsidePanelInput())"
                    + ".add(newModalInput())"
                    + ".add(settingsDetailPage.numberEditInput())"
                    + ".add(newBannerInput())"
                    + ".add(newScreenEscInput())"
                    + ".add(newRootPageInput())"
                    + ".add(newWorldChatInput());";

    /** The prose contract that carries the priority list, quoted from the source. */
    private static final String FROZEN_PRIORITY_PROSE =
            "Priority (first registered wins): closing guard, foreign chat UI, outside-panel close, modals (cropper/colour "
                    + "picker), pushed-subpage Esc, screen Esc, root pages, world chat.";

    private static final String FROZEN_NO_BRANCHES_PROSE =
            "New interactive features register a handler instead of inserting branches "
                    + "into the former if-else chains.";

    // ---------------------------------------------------------------- helpers

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("platforms"))) {
                return p;
            }
        }
        throw new AssertionError("repo root (the directory holding platforms/) not found above " + dir);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    /**
     * The gate field's whole declaration statement: from the declaration to the
     * first {@code ;} that sits outside every {@code (} and {@code &#123;}, so the
     * anonymous Host's own semicolon and every {@code .add(...)} are included.
     */
    private static String gateStatement(String source) {
        int from = source.indexOf(GATE_DECLARATION);
        assertTrue(from >= 0, "the gate field declaration was not found: " + GATE_DECLARATION);
        int depth = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return source.substring(from, i + 1);
            }
        }
        throw new AssertionError("the gate declaration is never terminated by ';'");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static String flatten(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * The javadoc block immediately above the gate declaration.
     *
     * <p>Scoped rather than searched file-wide: the contract prose is wrapped
     * across lines, so it only matches once the comment leaders are stripped,
     * and stripping them from the whole file would let unrelated fragments
     * assemble the sentence by accident.
     */
    private static String gateJavadoc(String source) {
        int decl = source.indexOf(GATE_DECLARATION);
        assertTrue(decl >= 0, "the gate field declaration was not found: " + GATE_DECLARATION);
        int start = source.lastIndexOf("/**", decl);
        assertTrue(start >= 0, "no javadoc found above the gate declaration");
        return source.substring(start, decl);
    }

    /** Comment leaders stripped and whitespace collapsed, so wrapped prose reads as one run. */
    private static String flattenComment(String comment) {
        return comment.replaceAll("(?m)^\\s*\\*\\s?", "").replaceAll("\\s+", "");
    }

    // ------------------------------------------------------------ assertions

    /**
     * There is exactly one handler chain and it is the gate's own.
     *
     * <p>This is the defect guard. A screen that declares a second chain — the
     * shape that shipped an InputRouter field beside the gate — has a gate whose
     * router is empty, so {@link InputGate} answers "not consumed" for every
     * event and the overrides fall through to vanilla. Hence both halves: no
     * {@code InputRouter} anywhere in the screen (the gate owns it, so the
     * screen needs neither the type nor the import), and the gate statement
     * itself carrying all nine registrations.
     */
    @Test
    void theScreenHasExactlyOneHandlerChainAndItIsTheGatesOwn() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertFalse(src.contains("InputRouter"),
                    screen + ": the screen must not build or name an InputRouter of its own —"
                            + " the gate owns the chain. A second chain leaves the gate's router"
                            + " empty, and every event falls through to super.");
            String gate = gateStatement(src);
            assertEquals(9, occurrences(gate, ".add("),
                    screen + ": the gate declaration must register exactly the nine handlers.\n"
                            + gate);
            assertTrue(flatten(gate).endsWith(".add(newWorldChatInput());"),
                    screen + ": the gate chain must end on WorldChatInput, the lowest-priority"
                            + " handler.\n" + gate);
        }
    }

    /**
     * The nine registrations keep their frozen relative order. Registration
     * order <em>is</em> the priority contract: a swap silently changes which
     * handler sees an event first, which is how the modal/root/world handlers
     * stay out of each other's way.
     */
    @Test
    void theHandlerChainIsRegisteredInTheFrozenPriorityOrder() throws IOException {
        for (String screen : SCREENS) {
            String gate = gateStatement(source(screen));
            assertTrue(flatten(gate).contains(FROZEN_CHAIN_FLAT),
                    screen + ": the registration order changed. Frozen order is ClosingState -> OutsidePanel ->"
                            + " Modal -> NumberEdit -> Banner -> ScreenEsc -> RootPage ->"
                            + " WorldChat.\n" + gate);
        }
    }

    /**
     * The prose that states the order contract travels with the code: the
     * priority list and the "register a handler, never insert a branch" rule are
     * the reason the chain is shaped this way, and both are quoted from the
     * source rather than paraphrased.
     */
    @Test
    void theOrderContractProseSurvives() throws IOException {
        for (String screen : SCREENS) {
            String doc = flattenComment(gateJavadoc(source(screen)));
            assertTrue(doc.contains(flatten(FROZEN_PRIORITY_PROSE)),
                    screen + ": the priority-list contract text was dropped or reworded.");
            assertTrue(doc.contains(flatten(FROZEN_NO_BRANCHES_PROSE)),
                    screen + ": the 'register a handler instead of inserting branches' rule was"
                            + " dropped or reworded.");
        }
    }

    /**
     * Every input override asks the gate, and every override is still a
     * delegation: six entry points, six {@code super} fallbacks. An override
     * that stopped calling the gate would be invisible to {@link InputGateTest}
     * in exactly the same way the empty chain was.
     */
    @Test
    void everyInputOverrideRoutesThroughTheGate() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String entry : List.of("gate.scroll(", "gate.click(", "gate.drag(",
                    "gate.release(", "gate.key(", "gate.charTyped(")) {
                assertEquals(1, occurrences(src, entry),
                        screen + ": expected exactly one " + entry + "...) call site");
            }
            for (String fallback : List.of("super.mouseScrolled(", "super.mouseClicked(",
                    "super.mouseDragged(", "super.mouseReleased(", "super.keyPressed(",
                    "super.charTyped(")) {
                assertEquals(1, occurrences(src, fallback),
                        screen + ": every override must keep its vanilla fallback (" + fallback + ")");
            }
        }
    }

    /**
     * The gate field is declared after every field its chain reads.
     *
     * <p>An initialiser may only read fields declared before it; the chain hands
     * {@code settingsDetailPage.numberEditInput()} to the gate, so the settings
     * page has to come first. This is the D21a/D22 forward-reference lesson,
     * asserted rather than remembered.
     */
    @Test
    void theGateIsDeclaredAfterTheFieldsItsChainReads() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            int settings = src.indexOf("private final SettingsDetailPage settingsDetailPage = ");
            int gate = src.indexOf(GATE_DECLARATION);
            assertTrue(settings >= 0, screen + ": settingsDetailPage declaration not found");
            assertTrue(gate > settings,
                    screen + ": the gate chain reads settingsDetailPage.numberEditInput(), so the"
                            + " gate must be declared after it (found settings at " + settings
                            + ", gate at " + gate + ")");
        }
    }
}
