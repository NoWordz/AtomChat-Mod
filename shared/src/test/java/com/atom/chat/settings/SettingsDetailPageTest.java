package com.atom.chat.settings;

import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SettingsDetailPage}'s offline half: the per-chip scroll memory, the
 * leave-the-page cleanup, the numeric editor's key handling, and the modal
 * contract on the settings draw path.
 *
 * <p><b>Scope, stated up front.</b> The real collaborators are used — a real
 * section page, a real scroll service, real modals, a real Skija raster surface
 * — because all of them work headless and that is exactly what makes the modal
 * contract checkable: with the cropper active, a frame that does not render it
 * is a frozen screen, and the frame below proves it is rendered. NOT covered:
 * what the modals do with a click (their own domains' tests), the numbers the
 * sliders commit to their config fields (D16/D18), and the pages these frames
 * sit in (the shell's draw loop, D25).</p>
 */
class SettingsDetailPageTest {
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    /** Deliberately short: the settings sub-page must overflow it, or nothing scrolls. */
    private static final float PANEL_H = 200.0F;
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;

    private static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        platformRoot = root;
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /** The real objects the domain drives, plus the two facts the shell owns. */
    private static final class Harness {
        String clipboardText = "";
        final SettingsSectionPage sectionPage = new SettingsSectionPage();
        final ColorPickerOverlay colorPicker = new ColorPickerOverlay(text -> {
        });
        final ImageCropper cropper = new ImageCropper(new ImageCropper.Callback() {
            @Override
            public void onConfirm(String targetId, byte[] pngBytes) {
            }

            @Override
            public void onCancel(String targetId) {
            }
        });
        final ScrollSyncService scrollSync = new ScrollSyncService(new ScrollSyncService.Host() {
            @Override
            public long frameDt() {
                return 16L;
            }

            @Override
            public com.atom.chat.nav.AppPage topPage() {
                return null;
            }

            @Override
            public com.atom.chat.chat.PlayerRef activePrivateTarget() {
                return null;
            }

            @Override
            public DrawPrimitives primitives() {
                return new DrawPrimitives(new DrawPrimitives.Host() {
                    @Override
                    public float frameDt() {
                        return 16.0F;
                    }

                    @Override
                    public boolean emojiPanelOpen() {
                        return false;
                    }

                    @Override
                    public boolean phrasePanelOpen() {
                        return false;
                    }
                });
            }
        });
        final UiLayout layout = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        final SettingsDetailPage page = new SettingsDetailPage(new SettingsDetailPage.Host() {
            @Override
            public SettingsSectionPage sectionPage() {
                return sectionPage;
            }

            @Override
            public ColorPickerOverlay colorPicker() {
                return colorPicker;
            }

            @Override
            public ImageCropper imageCropper() {
                return cropper;
            }

            @Override
            public ScrollSyncService scrollSync() {
                return scrollSync;
            }

            @Override
            public UiLayout detailLayout() {
                return layout;
            }

            @Override
            public int accent() {
                return 0xFF3B82F6;
            }

            @Override
            public float toVirtualX(double guiX) {
                return (float) guiX - PANEL_X;
            }

            @Override
            public float toVirtualY(double guiY) {
                return (float) guiY - PANEL_Y;
            }

            @Override
            public String clipboard() {
                return clipboardText;
            }
        });
    }

    @Test
    void presetNamePasteKeepsUnicodeCodePointsAndDoesNotLeakKeys() {
        Harness harness = new Harness();
        harness.clipboardText = "𠮷😀夜晚";
        harness.sectionPage.beginThemePresetNameEdit("save", null, "");
        InputHandler input = harness.page.numberEditInput();
        assertTrue(input.onKey(86, 0, 2));
        assertTrue(input.onKey(257, 0, 0));
        assertEquals("𠮷😀夜晚", harness.sectionPage.committedTextValue());
        assertFalse(harness.sectionPage.isEditingText());
    }

    @Test
    void presetNameEscapeCancelsAndCharacterInputIsConsumed() {
        Harness harness = new Harness();
        AtomicInteger commits = new AtomicInteger();
        harness.sectionPage.setActionHandler(action -> commits.incrementAndGet());
        harness.sectionPage.beginThemePresetNameEdit("save", null, "");
        InputHandler input = harness.page.numberEditInput();
        assertTrue(input.onChar('夜', 0));
        assertTrue(input.onScroll(0, 0, 0, 1));
        assertTrue(input.onKey(256, 0, 0));
        assertFalse(harness.sectionPage.isEditingText());
        assertEquals(0, commits.get());
    }

    @Test
    void visiblePresetCancelCanBeClickedWithoutSaving() {
        Harness harness = new Harness();
        AtomicInteger commits = new AtomicInteger();
        harness.sectionPage.setActionHandler(action -> commits.incrementAndGet());
        harness.sectionPage.beginThemePresetNameEdit("save", null, "夜晚");
        try (Surface surface = Surface.makeRasterN32Premul(420, 1000)) {
            harness.sectionPage.render(surface.getCanvas(), UiLayout.ofDetail(10, 10, 400, 980),
                    SettingsSection.APPEARANCE, -1, -1, 0, 0xFF4488CC);
            int y = 0;
            while (y < 1000 && !harness.sectionPage.isTextEditCancelHit(350, y)) y++;
            assertTrue(y < 1000, "a rendered cancel target must be reachable");
            assertTrue(harness.page.numberEditInput().onClick(350 + PANEL_X, y + PANEL_Y, 0));
        }
        assertFalse(harness.sectionPage.isEditingText());
        assertEquals(0, commits.get());
    }

    private static int[] drawnBounds(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            byte[] pixels = bitmap.readPixels();
            int x1 = Integer.MAX_VALUE;
            int y1 = Integer.MAX_VALUE;
            int x2 = -1;
            int y2 = -1;
            for (int y = 0; y < SURFACE_H; y++) {
                for (int x = 0; x < SURFACE_W; x++) {
                    if (pixels[(y * SURFACE_W + x) * 4 + 3] != 0) {
                        x1 = Math.min(x1, x);
                        x2 = Math.max(x2, x);
                        y1 = Math.min(y1, y);
                        y2 = Math.max(y2, y);
                    }
                }
            }
            return x2 < 0 ? null : new int[]{x1, y1, x2, y2};
        } finally {
            bitmap.close();
        }
    }

    @Test
    void theChipMemoryRestoresWhereEachChipWasLeft() {
        Harness harness = new Harness();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            Canvas canvas = surface.getCanvas();
            SettingsSection section = SettingsSection.CHAT;
            assertTrue(harness.sectionPage.chips(section).size() > 1,
                    "this test needs a section with more than one chip");

            harness.sectionPage.selectChip(section, 0);
            harness.page.drawSettingsSection(canvas, 0, 0, section);

            // Scroll away on chip 0. The frame clamps to the real content, so the
            // value to remember is read back rather than assumed.
            harness.scrollSync.detailScroll().scrollTo(200.0F, false);
            harness.page.drawSettingsSection(canvas, 0, 0, section);
            float left = harness.scrollSync.detailScroll().getScrollY();
            assertTrue(left > 0.0F, "the chat sub-page should be scrollable for this test: " + left);

            harness.sectionPage.selectChip(section, 1);
            harness.page.drawSettingsSection(canvas, 0, 0, section);
            assertEquals(0.0F, harness.scrollSync.detailScroll().getScrollY(), 0.5F,
                    "a chip never visited starts at the top");

            harness.sectionPage.selectChip(section, 0);
            harness.page.drawSettingsSection(canvas, 0, 0, section);
            assertEquals(left, harness.scrollSync.detailScroll().getScrollY(), 0.5F,
                    "coming back to a chip lands where it was left");
        } finally {
            surface.close();
        }
    }

    @Test
    void leavingThePageClearsTheScrollThePageStateAndTheChipMemory() {
        Harness harness = new Harness();
        SettingsSlider slider = slider();
        harness.sectionPage.beginNumberEdit(slider);
        harness.scrollSync.detailScroll().scrollTo(200.0F, false);

        harness.page.resetSettingsUi();

        assertEquals(0.0F, harness.scrollSync.detailScroll().getScrollY(), 0.5F);
        assertFalse(harness.sectionPage.isEditingNumber(), "the sub-page's transient state goes too");
        // The chip memory is gone as well: the next frame starts from the top
        // instead of restoring what the previous visit had remembered.
        harness.scrollSync.detailScroll().scrollTo(200.0F, false);
        harness.page.drawSettingsSection(Surface.makeRasterN32Premul(4, 4).getCanvas(), 0, 0,
                SettingsSection.APPEARANCE);
        assertEquals(0.0F, harness.scrollSync.detailScroll().getScrollY(), 0.5F);
    }

    @Test
    void theNumericEditorTakesDigitsFromTheKeysAndSwallowsTheCharCallback() {
        Harness harness = new Harness();
        AtomicInteger committed = new AtomicInteger(-1);
        SettingsSlider slider = slider(committed);
        InputHandler editor = harness.page.numberEditInput();

        // Nothing is being edited: every handler stays out of the way.
        assertFalse(editor.onKey(49, 0, 0));
        assertFalse(editor.onChar('1', 0));
        assertFalse(editor.onScroll(0, 0, 0, 0));
        assertFalse(editor.onClick(0, 0, 0));

        harness.sectionPage.beginNumberEdit(slider);
        // The buffer starts from the slider's current value (30), so the digits
        // below build "301": if the char callback appended too, or backspace did
        // nothing, the number would come out longer and clamp to the slider max.
        assertTrue(editor.onKey(49, 0, 0), "a digit is consumed while editing"); // '1' -> 301
        assertTrue(editor.onChar('1', 0), "the char callback is swallowed, not appended");
        assertTrue(editor.onKey(259, 0, 0), "backspace is consumed"); // Backspace -> 30
        assertTrue(editor.onKey(321, 0, 0), "numpad digits are consumed"); // numpad '1' -> 301
        assertTrue(editor.onKey(257, 0, 0)); // Enter commits

        assertEquals(301, committed.get(), "301 stays 301 only if the char was swallowed and backspace deleted");
        assertFalse(harness.sectionPage.isEditingNumber(), "committing ends the edit");
    }

    @Test
    void escapeCancelsWithoutTouchingTheSlider() {
        Harness harness = new Harness();
        AtomicInteger committed = new AtomicInteger(-1);
        InputHandler editor = harness.page.numberEditInput();
        harness.sectionPage.beginNumberEdit(slider(committed));

        assertTrue(editor.onKey(55, 0, 0)); // '7' appends to the current value
        assertTrue(editor.onKey(256, 0, 0)); // Esc

        assertEquals(-1, committed.get(), "a cancelled edit commits nothing");
        assertFalse(harness.sectionPage.isEditingNumber());
    }

    @Test
    void anActiveModalIsStillDrawnOnTheSettingsPath() throws Exception {
        Harness harness = new Harness();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            Canvas canvas = surface.getCanvas();
            Path source = platformRoot.resolve("picked.png");
            Files.createDirectories(source.getParent());
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
            // Both modals active: the contract says this frame renders them, and
            // an active-but-unrendered modal is a frozen screen (0.2.x).
            harness.cropper.open(source, false, 1.0F, 1.0F, "banner");
            harness.colorPicker.open(new SettingsColor("bubble_text", "atomchat.settings.bubbleText", "bubbles",
                    new int[]{0xFF112233}, () -> 0xFF112233, value -> {
                    }));

            harness.page.drawSettingsSection(canvas, 100, 100, SettingsSection.APPEARANCE);

            int[] bounds = drawnBounds(surface);
            assertNotNull(bounds, "the settings page with both modals active painted nothing");
            assertTrue(bounds[2] - bounds[0] + 1 >= 100, "the painted area is suspiciously narrow: " + bounds[2]);
        } finally {
            surface.close();
        }
    }

    @Test
    void allSettingsGroupsStartExpanded() {
        var page = new SettingsSectionPage();
        assertTrue(page.rows(SettingsSection.APPEARANCE).stream()
                .anyMatch(row -> row.kind() == SettingsSectionPage.RowKind.COLOR),
                "appearance color controls must be visible without expanding a group");
        int notify = java.util.stream.IntStream.range(0, page.chips(SettingsSection.CHAT).size())
                .filter(i -> page.chips(SettingsSection.CHAT).get(i).id().equals("notify"))
                .findFirst().orElseThrow();
        page.selectChip(SettingsSection.CHAT, notify);
        var ids = page.rows(SettingsSection.CHAT).stream().filter(row -> row.item() != null)
                .map(row -> row.item().id()).collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.containsAll(java.util.Set.of("notification", "mention_sound", "whisper_sound",
                "feedback", "feedback_button", "feedback_send", "feedback_panel")),
                "notification and feedback controls must start visible");
        assertTrue(page.rows(SettingsSection.ABOUT).stream().anyMatch(row -> row.item() != null
                && row.item().id().equals("debug")), "the about advanced group must start expanded");
    }

    /** A retention-days style slider whose commit lands in an int the test can read. */
    private static SettingsSlider slider() {
        return slider(new AtomicInteger(-1));
    }

    private static SettingsSlider slider(AtomicInteger committed) {
        return new SettingsSlider("retention_days", "atomchat.settings.retention", 1.0F, 365.0F, 1.0F,
                () -> 30.0F, value -> committed.set((int) value), value -> String.valueOf((int) value));
    }
}
