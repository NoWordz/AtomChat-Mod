package com.atom.chat.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QuarkEmotesCompatTest {
    private QuarkEmotesCompat compat;
    private Runnable renderer;

    @BeforeEach
    void setUp() {
        FakeModule.emotesVisible = false;
        FakeClient.emoteToggleButton = new FakeButton(31, 47, 83, 19);
        FakeClient.emoteButtons = new ArrayList<>(List.of(
                new FakeButton(-17, 71, 13, 23), new FakeButton(7, 71, 13, 23)));
        compat = new QuarkEmotesCompat(FakeClient.class.getName());
        renderer = FakeClient.renderer();
    }

    @Test
    void publicEntryPointIsSafeWithoutQuark() {
        assertFalse(QuarkEmotesCompat.hovered(null, 32, 48));
        assertFalse(QuarkEmotesCompat.hovered(List.of(), 32, 48));
        assertFalse(QuarkEmotesCompat.hovered(Arrays.asList(null, new Object(), renderer), 32, 48));
    }

    @Test
    void requiresOwnerInCurrentRenderablesEvenAfterEarlierHit() {
        assertTrue(hit(32, 48));
        assertFalse(compat.isHovered(List.of(), 32, 48));
        assertFalse(compat.isHovered(Arrays.asList(null, new UnrelatedRenderer()), 32, 48));
        assertFalse(compat.isHovered(null, 32, 48));
        assertTrue(hit(32, 48));
    }

    @Test
    void findsLambdaAmongUnrelatedRenderablesWithoutRunningIt() {
        assertTrue(compat.isHovered(Arrays.asList(null, new Object(), renderer), 32, 48));
    }

    @Test
    void toggleUsesActualPrivateBoundsWithInclusiveTopLeftAndExclusiveBottomRight() {
        assertTrue(hit(31, 47));
        assertTrue(hit(113.999, 65.999));
        assertFalse(hit(30.999, 48));
        assertFalse(hit(32, 46.999));
        assertFalse(hit(114, 48));
        assertFalse(hit(32, 66));
    }

    @Test
    void choicesFollowInheritedVisibilityOnEveryCall() {
        assertFalse(hit(-16, 72));
        FakeModule.emotesVisible = true;
        assertTrue(hit(-17, 71));
        assertTrue(hit(19.999, 93.999));
        assertFalse(hit(-4, 72));
        assertFalse(hit(-16, 94));
        assertFalse(hit(0, 72), "gaps between buttons must remain unprotected");
        FakeModule.emotesVisible = false;
        assertFalse(hit(-16, 72));
        assertTrue(hit(32, 48), "the closed panel still has its toggle");
    }

    @Test
    void readsReplacementButtonsAndListsAfterResize() {
        FakeModule.emotesVisible = true;
        assertTrue(hit(32, 48));
        assertTrue(hit(-16, 72));
        FakeClient.emoteToggleButton = new FakeButton(301, 211, 9, 7);
        FakeClient.emoteButtons = new ArrayList<>(List.of(new FakeButton(275, 211, 11, 7)));
        assertFalse(hit(32, 48));
        assertFalse(hit(-16, 72));
        assertTrue(hit(301, 211));
        assertTrue(hit(275, 211));
        FakeClient.emoteButtons.clear();
        assertFalse(hit(275, 211));
    }

    @Test
    void nullUninitializedStateCanRecoverWithoutRebuildingAccessors() {
        FakeClient.emoteToggleButton = null;
        FakeClient.emoteButtons = null;
        FakeModule.emotesVisible = true;
        assertFalse(hit(32, 48));
        FakeClient.emoteToggleButton = new FakeButton(31, 47, 83, 19);
        assertTrue(hit(32, 48));
        FakeClient.emoteButtons = List.of(new FakeButton(7, 71, 13, 23));
        assertTrue(hit(8, 72));
    }

    @Test
    void malformedChoiceEntriesDoNotHideOtherValidButtons() {
        FakeModule.emotesVisible = true;
        // A future/foreign modification can pollute the erased List at runtime.
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<FakeButton> polluted = (List) Arrays.asList(null, new Object(), new FakeButton(7, 71, 13, 23));
        FakeClient.emoteButtons = polluted;
        assertTrue(hit(8, 72));
        assertFalse(hit(0, 72));
    }

    @Test
    void invalidDimensionsAndNonFiniteCoordinatesNeverClaimHits() {
        for (FakeButton invalid : List.of(new FakeButton(31, 47, 0, 19),
                new FakeButton(31, 47, 83, 0), new FakeButton(31, 47, -1, 19),
                new FakeButton(31, 47, 83, -1))) {
            FakeClient.emoteToggleButton = invalid;
            assertFalse(hit(31, 47));
        }
        FakeClient.emoteToggleButton = new FakeButton(31, 47, 83, 19);
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertFalse(hit(invalid, 48));
            assertFalse(hit(32, invalid));
        }
    }

    @Test
    void coordinateAdditionDoesNotOverflowAtIntegerLimit() {
        FakeClient.emoteToggleButton = new FakeButton(Integer.MAX_VALUE - 2, 47, 10, 19);
        assertTrue(hit(2147483646.5, 48));
        assertFalse(hit(2147483655.0, 48));
    }

    @Test
    void supportsVisibilityDeclaredPrivatelyOnClientToo() {
        QuarkEmotesCompat direct = new QuarkEmotesCompat(DirectClient.class.getName());
        assertTrue(direct.isHovered(List.of(new DirectClient.Renderer()), 4, 6));
        assertTrue(direct.isHovered(List.of(new DirectClient.Renderer()), 21, 6));
    }

    @Test
    void unsupportedSchemasAreSafeNoOpsAcrossRepeatedFrames() {
        for (Object owner : List.of(new MissingFieldsClient(), new InstanceFieldsClient(),
                new WrongVisibilityClient(), new WrongBoundsClient(), new WrongListClient())) {
            QuarkEmotesCompat unsupported = new QuarkEmotesCompat(owner.getClass().getName());
            for (int frame = 0; frame < 3; frame++) {
                assertFalse(unsupported.isHovered(List.of(owner), 4, 6), owner.getClass().getName());
            }
        }
    }

    @Test
    void missingOwnerIsLookedUpOnceAndOnlyAfterMatchingCurrentRenderable() throws Exception {
        assertOwnerLookupFailureCached(false);
    }

    @Test
    void missingDependencyIsAlsoCachedAsUnavailable() throws Exception {
        assertOwnerLookupFailureCached(true);
    }

    @Test
    void readFailureDisablesAccessInsteadOfThrowingAgainEveryFrame() {
        AtomicInteger attempts = new AtomicInteger();
        FakeModule.emotesVisible = true;
        FakeClient.emoteButtons = new AbstractList<>() {
            @Override
            public FakeButton get(int index) { throw new UnsupportedOperationException(); }

            @Override
            public int size() { return 1; }

            @Override
            public Iterator<FakeButton> iterator() {
                attempts.incrementAndGet();
                throw new IllegalStateException("Incompatible collection");
            }
        };
        for (int frame = 0; frame < 10; frame++) {
            assertFalse(hit(-16, 72));
        }
        assertEquals(1, attempts.get(), "read failures must not recur every frame");
    }

    private void assertOwnerLookupFailureCached(boolean linkageFailure) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String ownerName = DirectClient.class.getName();
        String rendererName = DirectClient.Renderer.class.getName();
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/" + rendererName.replace('.', '/') + ".class")) {
            if (stream == null) throw new IOException("Missing renderer fixture bytecode");
            bytes = stream.readAllBytes();
        }
        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(ownerName)) {
                    attempts.incrementAndGet();
                    if (linkageFailure) throw new NoClassDefFoundError("Missing Quark dependency");
                    throw new ClassNotFoundException(name);
                }
                if (name.equals(rendererName)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        };
        var constructor = isolated.loadClass(rendererName).getDeclaredConstructor();
        constructor.setAccessible(true);
        Object orphanRenderer = constructor.newInstance();
        QuarkEmotesCompat missing = new QuarkEmotesCompat(ownerName);
        assertFalse(missing.isHovered(List.of(new Object()), 4, 6));
        assertEquals(0, attempts.get(), "unrelated screens must not resolve Quark");
        for (int frame = 0; frame < 10; frame++) {
            assertFalse(missing.isHovered(List.of(orphanRenderer), 4, 6));
        }
        assertEquals(1, attempts.get(), "failed resolution must be cached, not retried every frame");
    }

    private boolean hit(double x, double y) {
        return compat.isHovered(List.of(renderer), x, y);
    }

    private static class FakeModule {
        private static boolean emotesVisible;
    }

    private static final class UnrelatedRenderer implements Runnable {
        @Override
        public void run() { throw new AssertionError("Must not render"); }
    }

    private static final class FakeClient extends FakeModule {
        private static FakeButton emoteToggleButton;
        private static List<FakeButton> emoteButtons;

        static Runnable renderer() {
            return () -> { throw new AssertionError("Hover must never draw or click Quark's buttons"); };
        }
    }

    private static final class FakeButton {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        FakeButton(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class DirectClient {
        private static final FakeButton emoteToggleButton = new FakeButton(3, 5, 7, 11);
        private static final List<FakeButton> emoteButtons = List.of(new FakeButton(20, 5, 7, 11));
        private static final boolean emotesVisible = true;

        static final class Renderer implements Runnable {
            @Override
            public void run() { throw new AssertionError("Must not render"); }
        }
    }

    private static final class MissingFieldsClient { }

    private static final class InstanceFieldsClient {
        private final FakeButton emoteToggleButton = new FakeButton(3, 5, 7, 11);
        private final List<FakeButton> emoteButtons = List.of();
        private final boolean emotesVisible = true;
    }

    private static final class WrongVisibilityClient {
        private static final FakeButton emoteToggleButton = new FakeButton(3, 5, 7, 11);
        private static final List<FakeButton> emoteButtons = List.of();
        private static final String emotesVisible = "true";
    }

    private static final class WrongBoundsClient {
        private static final Object emoteToggleButton = new Object();
        private static final List<Object> emoteButtons = List.of();
        private static final boolean emotesVisible = true;
    }

    private static final class WrongListClient {
        private static final FakeButton emoteToggleButton = new FakeButton(3, 5, 7, 11);
        private static final Object emoteButtons = new Object();
        private static final boolean emotesVisible = true;
    }
}
