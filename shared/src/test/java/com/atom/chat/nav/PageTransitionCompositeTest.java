package com.atom.chat.nav;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.Animator;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.settings.ProfilePageWiring;
import com.atom.chat.settings.SettingsDetailPage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.ComposerInput;
import com.atom.chat.ui.ContextMenuActions;
import com.atom.chat.ui.ContextMenuController;
import com.atom.chat.ui.HeaderChrome;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageTransitionComposite} on a real raster surface: six paths, each judged
 * on geometry <em>and</em> on where the pixels actually landed.
 *
 * <p><b>Why this file exists.</b> Until this block every fact about the composite
 * was a source-guard fact. {@code PageTransitionDirectorTest} pins the clocks by
 * value and {@code PageNavStyleSourceTest} asserts "the page being left slides out
 * and the arriving one enters from the other side" by reading the screen's own
 * text — but nothing had ever <em>composited a transition</em> and looked at the
 * frame. The golden scenes render one page object at a time and never composite,
 * so the whole class of mistake "the geometry reads right in the source and lands
 * wrong on the canvas" had no offline gate at all.
 *
 * <p><b>What is judged, and how.</b> The page bodies sit behind
 * {@link PageTransitionComposite.Host} ports, so this test hands the composite a
 * recording host whose chat-page body fills one flat colour and remembers the
 * matrix the canvas was in at that moment, while the root body and the two
 * full-page drawers are the real objects. Every path is then asserted twice:
 * <ul>
 *   <li><b>geometry</b> — the translation/scale the composite asked the canvas
 *       for, read back from {@code getLocalToDeviceAsMatrix33()};</li>
 *   <li><b>pixels</b> — a scan of the middle row of the surface: which side's
 *       colour landed where, and in which frame.</li>
 * </ul>
 * Either half alone would pass a wrong frame: the geometry can be right while the
 * draw is skipped, and a colour can land on the right side by accident while the
 * offset is wrong.
 *
 * <p><b>The headline case is the pop.</b> Push and pop assign the two offsets by
 * <em>role</em>, never by page kind, and both pages here are non-root chat pages —
 * so the only thing that can put the two colours on opposite sides is the
 * push/pop distinction itself. That is the pixel evidence
 * {@code PageNavStyleSourceTest} could not give.
 *
 * <p><b>Scope, stated up front.</b> What is asserted is the composite's own
 * geometry, layer order and pass structure. The real {@code drawChatPageBody} is
 * <em>not</em> covered here — it stays on the screen by ruling and has its own
 * wiring guard — and neither is page content, which the golden scenes cover. The
 * panel shell's remaining chrome (bezel, wallpaper, blur, header composite, input
 * bar) still has no offline pixel coverage; the block report says so.
 */
class PageTransitionCompositeTest {

    // ------------------------------------------------------------ the surface

    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;

    /** The scan line: inside the panel (y 10..790) and inside every transform used here. */
    private static final int BAND_Y = 400;

    /** Rounding/pixel-centre slack when a computed edge is compared with a pixel index. */
    private static final int EDGE_TOLERANCE = 2;

    /**
     * The two layouts are deliberately different objects. The shell only reaches
     * its detail layout from the settings/profile early returns and the nav tail,
     * so a test that handed back one layout could not tell which port the
     * composite actually used.
     */
    private static final UiLayout DETAIL_LAYOUT = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
    private static final UiLayout ROOT_LAYOUT = UiLayout.ofRoot(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

    /**
     * The two body colours. Opaque and far apart on purpose: the band scan reads
     * the dominant channel, so a colour that moved must change the channel that
     * dominates its pixels.
     */
    private static final int RED = 0xFFE00000;
    private static final int CYAN = 0xFF00E0E0;
    /**
     * The root screen's own probe. A third colour on purpose: the chat body of a
     * WORLD_CHAT page is red and the root's body is also the world chat, so a shared
     * colour could not tell "the moving page landed on the root" from "the root is
     * showing through". Green owns the G channel alone.
     */
    private static final int GREEN = 0xFF00FF00;
    private static final String[] NAMES = {"red", "cyan", "green"};

    private static final NavPage WORLD = NavPage.of(AppPage.WORLD_CHAT);
    private static final NavPage PRIVATE =
            NavPage.privateChat(PlayerRef.of(new UUID(0x11L, 0x22L), "Alice"));
    private static final NavPage SETTINGS_SECTION = NavPage.settingsSection(SettingsSection.CHAT);
    private static final NavPage PROFILE_DETAIL = NavPage.of(AppPage.PROFILE_DETAIL);

    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;

    @BeforeEach
    void setUp() {
        AtomChatConfig config = AtomChatConfig.get();
        animations = config.animationEnabled;
        style = config.pageNavStyle;
        config.animationEnabled = true;
        config.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = animations;
        config.pageNavStyle = style;
    }

    // ----------------------------------------------------------- the fixtures

    /** What the composite asked the canvas for while one body drew. */
    private record Landing(String what, float dx, float dy, float scale) {
    }

    /**
     * The recording host: every port is a one-line forward, exactly as on the
     * screen. The chat body paints one flat colour and remembers its matrix; the
     * root body and the two drawers are the real classes, so the paths that reach
     * them are exercised end to end.
     */
    private static final class ProbeHost implements PageTransitionComposite.Host {
        final List<Landing> landings = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        AppPage top = AppPage.WORLD_CHAT;
        boolean headerSuppressed;
        /** Whether the root body paints its probe colour; some tests read only the offset. */
        boolean recordRootPixels = true;
        SettingsDetailPage settings;
        ProfilePageWiring profile;
        /** Makes the profile drawer report that the composite reached it, without drawing. */
        boolean profileIsSentinel;

        static Landing landing(String what, Canvas canvas) {
            float[] m = canvas.getLocalToDeviceAsMatrix33().getMat();
            return new Landing(what, m[2], m[5], m[0]);
        }

        static void block(Canvas canvas, UiLayout layout, int color) {
            try (Paint paint = new Paint().setColor(color).setMode(PaintMode.FILL)) {
                canvas.drawRect(Rect.makeXYWH(layout.panelX, layout.panelY,
                        layout.panelW, layout.panelH), paint);
            }
        }

        @Override
        public AppPage topPage() {
            return top;
        }

        @Override
        public void suppressHeader(boolean value) {
            headerSuppressed = value;
            calls.add("suppressHeader:" + value);
        }

        @Override
        public PageTransitionComposite.ChatPageView viewFor(NavPage page) {
            calls.add("viewFor:" + page.page());
            if (page.page() == AppPage.WORLD_CHAT) {
                return new PageTransitionComposite.ChatPageView(page, "", false, "", false,
                        null, new ScrollController(), true);
            }
            if (page.page() == AppPage.PRIVATE_CHAT) {
                return new PageTransitionComposite.ChatPageView(page, "", false, "", false,
                        null, new ScrollController(), false);
            }
            return null;
        }

        @Override
        public void drawChatPageBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                     PageTransitionComposite.ChatPageView view) {
            landings.add(landing("chat:" + view.page().page(), canvas));
            block(canvas, layout, view.page().page() == AppPage.WORLD_CHAT ? RED : CYAN);
        }

        @Override
        public void drawRootScreen(Canvas canvas, int mouseX, int mouseY, AppPage rootPage) {
            // The screen paints its root page in the ROOT layout (the composite only
            // supplies the transform), so the probe colour lands there.
            landings.add(landing("root:" + rootPage, canvas));
            float[] rm = canvas.getLocalToDeviceAsMatrix33().getMat();
            if (recordRootPixels) {
                block(canvas, ROOT_LAYOUT, GREEN);
            }
        }

        /** The composite draws the root page only inside the root<->detail composite. */
        boolean rootEverDrawn() {
            return landings.stream().anyMatch(l -> l.what().startsWith("root:"));
        }

        @Override
        public void drawBezel(Canvas canvas, UiLayout layout) {
            // The bezel is the last thing a frame paints. Its canvas state is the
            // regression witness for the fixed chrome: whatever the composite opened
            // must already be closed here, or the ring (and the status-bar header
            // painted just before it) rides the page transform.
            bezelSaveCount = canvas.getSaveCount();
            bezelTranslateX = canvas.getLocalToDeviceAsMatrix33().getMat()[2];
            calls.add("drawBezel:" + (layout == DETAIL_LAYOUT ? "detail" : "root"));
        }

        /** Save-stack depth when the bezel was painted (the frame's own base when clean). */
        int bezelSaveCount = -1;
        /** Horizontal transform when the bezel was painted: 0 when the chrome is fixed. */
        float bezelTranslateX = Float.NaN;

        @Override
        public UiLayout rootLayout() {
            return ROOT_LAYOUT;
        }

        @Override
        public UiLayout detailLayout() {
            return DETAIL_LAYOUT;
        }

        @Override
        public com.atom.chat.ui.ShellHeader.HeaderAction headerActionFor(NavPage page) {
            return null;
        }
    }

    /** The hidden field, standing in for the real one (an EditBox needs a game). */
    private static final class StubField implements ComposerInput.Field {
        String value = "";

        @Override
        public boolean present() {
            return true;
        }

        @Override
        public String text() {
            return value;
        }

        @Override
        public void setText(String text) {
            value = text;
        }

        @Override
        public int caretPosition() {
            return value.length();
        }

        @Override
        public boolean focused() {
            return false;
        }

        @Override
        public void setScreenFocus() {
        }

        @Override
        public void fieldFocus() {
        }

        @Override
        public void fieldFocusGuarded() {
        }

        @Override
        public void fieldBlur() {
        }

        @Override
        public void moveCaretTo(int pos) {
        }
    }

    /** The screen-side facts the director's three steps need. */
    private static final class NavRecorder implements PageTransitionDirector.Host {
        final List<String> calls = new ArrayList<>();

        @Override
        public void persistCurrentDraft() {
            calls.add("persistCurrentDraft");
        }

        @Override
        public void captureReplySlot() {
            calls.add("captureReplySlot");
        }

        @Override
        public void applyPendingPop() {
            calls.add("applyPendingPop");
        }
    }

    /** The composite with every collaborator it is built from, plus the surface. */
    private final class Fixture implements AutoCloseable {
        final Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        final Canvas canvas = surface.getCanvas();
        final int baseline = canvas.getSaveCount();
        final ProbeHost host = new ProbeHost();
        final NavRecorder nav = new NavRecorder();
        final PageTransitionDirector director = new PageTransitionDirector(nav, new Animator(t -> t));
        final HeaderChrome chrome;
        final ScrollSyncService scrollSync;
        /** How often a full-page drawer asked for the detail scroll (i.e. really ran). */
        int detailScrollReads;
        /** The handle the last begin reported. */
        PageTransitionComposite.NavFrame lastFrame;
        final PageTransitionComposite composite;

        Fixture() {
            DrawPrimitives primitives = new DrawPrimitives(new DrawPrimitives.Host() {
                @Override
                public float frameDt() {
                    return 0.0F;
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
            ComposerInput composer = new ComposerInput(new ComposerInput.Host() {
                @Override
                public UiLayout layout() {
                    return DETAIL_LAYOUT;
                }

                @Override
                public ScrollController currentScroll() {
                    return new ScrollController();
                }

                @Override
                public float frameDt() {
                    return 0.0F;
                }

                @Override
                public int clampInt(int value, int lo, int hi) {
                    return Math.max(lo, Math.min(hi, value));
                }
            }, new StubField());
            chrome = new HeaderChrome(new HeaderChrome.Host() {
                @Override
                public String label(String key) {
                    return key;
                }

                @Override
                public String activePrivateName() {
                    return null;
                }

                @Override
                public UiLayout.Rect headerRect() {
                    return DETAIL_LAYOUT.header;
                }

                @Override
                public UiLayout.Rect pushedHeaderRect() {
                    return DETAIL_LAYOUT.header;
                }

                @Override
                public float frameDt() {
                    return 0.0F;
                }

                @Override
                public int textPrimary() {
                    return 0xFF112233;
                }
            });
            ContextMenuController<String> menu = new ContextMenuController<>(new ContextMenuController.Host() {
                @Override
                public String label(String key) {
                    return key;
                }

                @Override
                public boolean isPlayerOnline(PlayerRef player) {
                    return false;
                }

                @Override
                public UiLayout.Rect panelRect() {
                    return DETAIL_LAYOUT.rect();
                }

                @Override
                public float frameDt() {
                    return 0.0F;
                }

                @Override
                public void drawIcon(Canvas canvas, io.github.humbleui.skija.Path icon,
                                     float cx, float cy, float size, int color) {
                }
            });
            ContextMenuActions actions = new ContextMenuActions(new ContextMenuActions.Host() {
                @Override
                public List<ContextMenuActions.OnlinePlayer> onlinePlayers() {
                    return List.of();
                }

                @Override
                public boolean hasLocalPlayer() {
                    return true;
                }

                @Override
                public String teleportCommand() {
                    return null;
                }

                @Override
                public void sendCommand(String command) {
                }

                @Override
                public void appendToComposer(String text) {
                }

                @Override
                public void privateChatWith(PlayerRef player) {
                }

                @Override
                public void profileDetailFor(PlayerRef player) {
                }

                @Override
                public PlayerRef currentOrLastPlayer() {
                    return null;
                }
            });
            scrollSync = new ScrollSyncService(new ScrollSyncService.Host() {
                @Override
                public long frameDt() {
                    detailScrollReads++;
                    return 0L;
                }

                @Override
                public AppPage topPage() {
                    return host.top;
                }

                @Override
                public PlayerRef activePrivateTarget() {
                    return null;
                }

                @Override
                public DrawPrimitives primitives() {
                    throw new AssertionError("this file does not draw the sync pill");
                }
            });
            ProfilePage profilePage = new ProfilePage(new ProfilePage.Handler() {
                @Override
                public void openAvatarPicker() {
                }

                @Override
                public void clearAvatar() {
                }

                @Override
                public void openBannerPicker() {
                }

                @Override
                public void clearBanner() {
                }

                @Override
                public boolean copyText(String value) {
                    return true;
                }

                @Override
                public void beginSignatureEdit() {
                }

                @Override
                public void endSignatureEdit(boolean commit) {
                }
            }, new AvatarStore(tmp.resolve("avatar")));
            host.profile = new ProfilePageWiring(new ProfilePageWiring.Host() {
                @Override
                public ProfilePage profilePage() {
                    if (host.profileIsSentinel) {
                        throw new AssertionError("profile sentinel reached");
                    }
                    return profilePage;
                }

                @Override
                public String composerText() {
                    return "";
                }

                @Override
                public void setComposerText(String text) {
                }

                @Override
                public void focusComposer() {
                }

                @Override
                public void caretToEnd() {
                }

                @Override
                public UiLayout detailLayout() {
                    return DETAIL_LAYOUT;
                }

                @Override
                public ScrollSyncService scrollSync() {
                    return scrollSync;
                }

                @Override
                public float toVirtualX(double guiX) {
                    return (float) guiX;
                }

                @Override
                public float toVirtualY(double guiY) {
                    return (float) guiY;
                }

                @Override
                public UUID localPlayerId() {
                    return null;
                }

                @Override
                public boolean localPlayerHasPermissions(int level) {
                    return false;
                }

                @Override
                public boolean composerFocused() {
                    return false;
                }
            });
            SettingsSectionPage sectionPage = new SettingsSectionPage();
            ImageCropper cropper = new ImageCropper(new ImageCropper.Callback() {
                @Override
                public void onConfirm(String targetId, byte[] pngBytes) {
                }

                @Override
                public void onCancel(String targetId) {
                }
            });
            ColorPickerOverlay picker = new ColorPickerOverlay(value -> {
            });
            host.settings = new SettingsDetailPage(new SettingsDetailPage.Host() {
                @Override
                public SettingsSectionPage sectionPage() {
                    return sectionPage;
                }

                @Override
                public ColorPickerOverlay colorPicker() {
                    return picker;
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
                    return DETAIL_LAYOUT;
                }

                @Override
                public int accent() {
                    return 0xFF3B82F6;
                }

                @Override
                public float toVirtualX(double guiX) {
                    return (float) guiX;
                }

                @Override
                public float toVirtualY(double guiY) {
                    return (float) guiY;
                }

            });
            composite = new PageTransitionComposite(host, director, composer, chrome, primitives,
                    menu, actions, host.profile, host.settings);
        }

        /**
         * A complete frame: cleared, composited, and the save stack must come back
         * balanced. Only frames that run to completion belong here — a frame whose
         * moving layer is left open for the shell's tail is deliberately one save
         * deep until {@code end} closes it (see {@link #beginFrame}).
         */
        void frame(String what, Runnable render) {
            render.run();
            assertEquals(baseline, canvas.getSaveCount(),
                    what + " left the canvas save stack unbalanced (a leaked save is how the 0.2.4"
                            + " notification banner leaked its matrix)");
        }

        /**
         * The composite half of one frame: {@code begin} on a cleared canvas. When the
         * frame it reports is finished the composite opened nothing, so the save stack
         * is asserted right here; when it is running, exactly one save-point is open and
         * the caller closes it through {@code end}.
         */
        PageTransitionComposite.NavFrame beginFrame(String what) {
            return beginFrame(what, 50L);
        }

        PageTransitionComposite.NavFrame beginFrame(String what, long frameDt) {
            canvas.clear(0);
            PageTransitionComposite.NavFrame frame = composite.begin(
                    canvas, DETAIL_LAYOUT, 0, 0, 0.0F, 0.0F, frameDt);
            lastFrame = frame;
            if (!frame.running()) {
                // A finished frame owns everything it opened: nothing may be left for
                // a tail that will never run.
                assertEquals(baseline, canvas.getSaveCount(),
                        what + " completed the frame but left a save open");
            } else {
                // What a running frame leaves open varies with style and phase (a
                // skipped zoom layer leaves nothing; a chat fall-through leaves one
                // save), so the depth is not asserted here — `endInFrame` checks that
                // the tail pops exactly what the handle names, and every other frame
                // goes through `frame`, which asserts the canvas comes back even.
                assertTrue(canvas.getSaveCount() >= baseline,
                        what + " popped past the canvas baseline");
            }
            return frame;
        }

        /**
         * The SCREEN's own half of a transition frame: the chat body, painted inside
         * the save the frame left open. The real shell does this between {@code begin}
         * and {@code end}; the test plays it here so the geometry and the pixels agree.
         */
        void drawArrivingBody(NavPage page) {
            host.drawChatPageBody(canvas, DETAIL_LAYOUT, 0, 0, host.viewFor(page));
        }

        /** The screen's half of a frame, addressed by the page the frame is heading for. */
        void drawArrivingBody() {
            drawArrivingBody(director.pageNavTo());
        }

        /**
         * ONE transition frame, start to finish, on one surface: the composite's
         * half ({@code begin}), the screen's half (the chat body the frame left a
         * save open for) and the tail ({@code end}), then the balance assertion.
         *
         * <p>This is the shape the real shell runs: {@code frame()} clears the
         * surface, so a tail started through it would wipe the very pixels the
         * frame just composited before the scan could read them. The tail belongs
         * to the same frame as the body — that is what {@code tailCloses} means.
         */
        PageTransitionComposite.NavFrame compositeFrame(String what, long frameDt,
                                                        Runnable screenHalf) {
            PageTransitionComposite.NavFrame frame = beginFrame(what, frameDt);
            screenHalf.run();
            if (frame.running()) {
                composite.end(canvas, DETAIL_LAYOUT, frame);
            }
            assertEquals(baseline, canvas.getSaveCount(),
                    what + " left the canvas save stack unbalanced (a leaked save is how the 0.2.4"
                            + " notification banner leaked its matrix)");
            return frame;
        }

        /** How many save-points the last begin opened: the plain save plus a real layer. */        int saveDepth() {
            return lastFrame != null && lastFrame.movingLayer() != null ? 1 : 0;
        }

        /**
         * Runs the nav tail on the SAME surface the frame was composited on, and asserts
         * the canvas comes back even. The tail belongs to its own frame — clearing the
         * surface here (as {@code frame} does) would both wipe the frame's pixels and
         * hide what the tail is for.
         */
        void endInFrame(String what, PageTransitionComposite.NavFrame frame) {
            assertTrue(frame.running(), what + ": there is no running nav frame to close");
            int before = canvas.getSaveCount();
            composite.end(canvas, DETAIL_LAYOUT, frame);
            assertEquals(before - 1 - (frame.movingLayer() != null ? 1 : 0),
                    canvas.getSaveCount(),
                    what + " must pop exactly the save-points the composite opened");
            assertEquals(baseline, canvas.getSaveCount(),
                    what + " left the canvas save stack unbalanced (a leaked save is how the 0.2.4"
                            + " notification banner leaked its matrix)");
        }

        @Override
        public void close() {
            surface.close();
        }
    }

    // -------------------------------------------------------------- the probes

    /** The colour runs of the middle row, left to right: {colour, firstX, lastX}. */
    private static List<int[]> runs(Surface surface) {
        byte[] px = readPixels(surface);
        List<int[]> out = new ArrayList<>();
        int current = -1;
        int start = 0;
        for (int x = 0; x < SURFACE_W; x++) {
            int at = (BAND_Y * SURFACE_W + x) * 4;
            int b = px[at] & 0xFF;
            int g = px[at + 1] & 0xFF;
            int r = px[at + 2] & 0xFF;
            // Only the flat probe colours are classified. The channels are chosen far
            // enough apart that premultiplying a faded probe colour still keeps its
            // stronger channel: the zoom's saveLayer writes the colour at the layer's
            // alpha, so a mid-zoom frame reads a fraction of it (0xFFE00000 at alpha
            // 0.37 comes back as (82, 0, 0) red and (0, 82, 82) cyan). Each probe lives
            // in its own channel family — red in R, cyan in G+B, the root's own probe in
            // G alone — so the dominant channel still tells them apart at any opacity,
            // while the chrome's own palette (dark greys and near-blacks) stays under
            // the floor. The alpha channel is deliberately not gated: a composited
            // layer's alpha is its own premultiplied value, so a hard floor would hide
            // exactly the faded frames this test is about.
            int colour = -1;
            if (r >= 20 || g >= 20 || b >= 20) {
                // Green first: the root screen's own probe is plain green, and it shares
                // no channel with either body colour, so "the root is under the moving
                // page" stays readable even where the two overlap.
                colour = g > r && g > b ? 2 : (b >= r && g >= r ? 1 : 0);
            }
            if (colour != current) {
                if (current >= 0) {
                    out.add(new int[]{current, start, x - 1});
                }
                current = colour;
                start = x;
            }
        }
        if (current >= 0) {
            out.add(new int[]{current, start, SURFACE_W - 1});
        }
        return out;
    }

    /** The x-range one colour occupies on the middle row, or null when it is absent. */
    private static int[] span(List<int[]> runs, int colour) {
        for (int[] run : runs) {
            if (run[0] == colour) {
                return new int[]{run[1], run[2]};
            }
        }
        return null;
    }




    private static String describe(List<int[]> runs) {
        StringBuilder sb = new StringBuilder("[");
        for (int[] run : runs) {
            sb.append(NAMES[run[0]]).append(' ').append(run[1]).append("..").append(run[2]).append(", ");
        }
        return sb.append(']').toString();
    }

    /** Bounding box {x1, y1, x2, y2} of the opaque pixels, or null when nothing was painted. */
    private static int[] drawnBounds(Surface surface) {
        byte[] px = readPixels(surface);
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int x2 = -1;
        int y2 = -1;
        for (int y = 0; y < SURFACE_H; y++) {
            for (int x = 0; x < SURFACE_W; x++) {
                if ((px[(y * SURFACE_W + x) * 4 + 3] & 0xFF) != 0) {
                    x1 = Math.min(x1, x);
                    x2 = Math.max(x2, x);
                    y1 = Math.min(y1, y);
                    y2 = Math.max(y2, y);
                }
            }
        }
        return x2 < 0 ? null : new int[]{x1, y1, x2, y2};
    }

    /** Bounding box of the opaque pixels inside one rect, or null when it is empty. */
    private static int[] drawnIn(Surface surface, UiLayout.Rect rect) {
        byte[] px = readPixels(surface);
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int x2 = -1;
        int y2 = -1;
        for (int y = Math.max(0, (int) rect.y()); y < Math.min(SURFACE_H, (int) rect.bottom()); y++) {
            for (int x = Math.max(0, (int) rect.x()); x < Math.min(SURFACE_W, (int) rect.right()); x++) {
                if ((px[(y * SURFACE_W + x) * 4 + 3] & 0xFF) != 0) {
                    x1 = Math.min(x1, x);
                    x2 = Math.max(x2, x);
                    y1 = Math.min(y1, y);
                    y2 = Math.max(y2, y);
                }
            }
        }
        return x2 < 0 ? null : new int[]{x1, y1, x2, y2};
    }

    private static byte[] readPixels(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            return bitmap.readPixels();
        } finally {
            bitmap.close();
        }
    }

    /** The landing the host recorded for one body label, asserting it happened. */
    private static Landing landing(ProbeHost host, String what) {
        for (Landing l : host.landings) {
            if (l.what().equals(what)) {
                return l;
            }
        }
        throw new AssertionError("the composite never asked for " + what + "; it asked for "
                + host.landings);
    }

    /**
     * Runs the slide clock in small frames until it is past {@code target} but still
     * short of settled. A threshold rather than a frame count on purpose: the clock's
     * exponential law is the director's business (and its tau is tuned there), so a
     * test that hardcoded "the third frame" would be a second copy of that law — and
     * it would silently stop being mid-slide the day a frame advances further.
     */
    private static void slideTo(PageTransitionDirector director, float target) {
        for (int i = 0; i < 500 && director.pageNavActive()
                && director.navSlideProgress() < target; i++) {
            director.stepSlideNav(4.0F);
        }
        assertTrue(director.pageNavActive(),
                "the fixture must still be mid-slide, reached " + director.navSlideProgress());
    }

    /** Advances the zoom clock until it hands over from EXIT to ENTER. */
    private static void stepToEnter(PageTransitionDirector director) {
        for (int i = 0; i < 40 && director.zoomExiting(); i++) {
            director.stepZoomNav(50.0F);
        }
        assertFalse(director.zoomExiting(), "the zoom never reached its ENTER phase");
    }

    private static void assertNear(int expected, int actual, String what) {
        assertTrue(Math.abs(expected - actual) <= EDGE_TOLERANCE,
                what + ": expected about " + expected + "px, measured " + actual + "px");
    }

    // --------------------------------------------------------- 1. the root page

    /**
     * The 11a branch: a root page on top paints the root screen, then the context
     * menu and the bezel, and none of the nav machinery runs — the composite reports
     * the frame as finished so the screen returns.
     */
    @Test
    void aRootPageTakesTheWholeFrameAndSkipsTheNavMachinery() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.CHAT_LIST;
            f.frame("root frame", () -> assertTrue(
                    f.composite.drawRootFrame(f.canvas, ROOT_LAYOUT, 0, 0, 0.0F, 0.0F),
                    "a root page is the whole frame"));

            assertEquals(1, f.host.landings.size(), "the root screen is painted exactly once");
            Landing root = landing(f.host, "root:CHAT_LIST");
            assertEquals(0.0F, root.dx(), 0.0F, "no nav offset on a settled root frame");
            assertEquals(1.0F, root.scale(), 1.0e-6F);
            // The bezel closes the frame, and it takes the settled layout there.
            assertTrue(f.host.calls.contains("drawBezel:root"),
                    "the bezel is the last thing in the panel: " + f.host.calls);
            // And no nav ran: an idle clock is never asked to step.
            assertEquals(List.of(), f.nav.calls, "no transition may be armed on a root frame");
        }
    }

    /**
     * The other half of the 11a branch: with a pushed page on top the composite
     * must not take the frame, and the pixels must say the root screen was never
     * painted.
     */
    @Test
    void aPushedPageIsNotTheRootFrame() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.WORLD_CHAT;
            f.frame("not a root frame", () -> assertFalse(
                    f.composite.drawRootFrame(f.canvas, DETAIL_LAYOUT, 0, 0, 0.0F, 0.0F),
                    "a pushed page runs through the nav machinery instead"));
            assertEquals(List.of(), f.host.landings, "nothing was painted by the early return");
        }
    }

    // ------------------------------------------------- 2. the slide, both ways

    /**
     * The fixed chrome never joins a transition's transform, on either style. A
     * detail&lt;-&gt;detail frame paints its page bodies inside the save it opened and
     * must close that save before the status-bar header and the bezel ring: leaving
     * it open (as the first cut of this block did) put the ring and the header inside
     * the slide/zoom transform, and handed the page body a second, un-faded draw on
     * top of the faded layer.
     *
     * <p>This is the assertion that catches it, and it is deliberately about canvas
     * state rather than about the calls: the wrapped ports see the same call sequence
     * either way.
     */
    @Test
    void theFixedChromeIsPaintedAfterTheFrameClosesItsOwnSaves() {
        for (AtomChatConfig.PageNavStyle style : List.of(
                AtomChatConfig.PageNavStyle.SLIDE, AtomChatConfig.PageNavStyle.ZOOM)) {
            AtomChatConfig.get().pageNavStyle = style;
            try (Fixture f = new Fixture()) {
                f.director.startPageNav(WORLD, PRIVATE, false);
                int base = f.canvas.getSaveCount();
                if (style == AtomChatConfig.PageNavStyle.SLIDE) {
                    slideTo(f.director, 0.15F);
                }
                PageTransitionComposite.NavFrame frame = f.compositeFrame(style + " detail frame", 50L,
                        () -> { });
                assertTrue(frame.finished(), style + ": a detail<->detail frame finishes itself");
                assertFalse(frame.tailCloses(), style + ": and leaves no save open for the screen");
                assertEquals(base, f.host.bezelSaveCount,
                        style + ": the bezel must be painted with every save the frame opened"
                                + " already closed");
                assertEquals(0.0F, f.host.bezelTranslateX, 1.0e-4F,
                        style + ": and with no page transform applied, so the fixed chrome is"
                                + " really fixed");
            }
        }
    }

    /**
     * A detail-to-detail push: the page being left slides out to the left across the
     * whole travel and the arriving one enters from the right. Judged on the two
     * matrices the composite asked for and on which colour ended up where.
     */
    @Test
    void aPushSlidesTheLeavingPageLeftAndBringsTheNewOneInFromTheRight() {
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(WORLD, PRIVATE, false);
            float travel = DETAIL_LAYOUT.list.w();
            assertEquals(travel, f.director.pageNavDx(travel), 1.0e-6F,
                    "a push starts with the arriving page a full travel to the right");
            slideTo(f.director, 0.15F);

            // The nav clock steps inside the frame too, so the shipped progress is the
            // director's answer AFTER it, not the one this test read before it.
            float[] shipped = new float[1];
            Landing[] sides = new Landing[2];
            PageTransitionComposite.NavFrame frame = f.compositeFrame("push slide", 50L, () -> {
                shipped[0] = f.director.navSlideProgress();
                // Both whole pages are the composite's own: a detail<->detail frame
                // paints its sides (the chat body included) and closes its own save, so
                // the screen's half of the frame does not exist on this path.
                sides[0] = landing(f.host, "chat:WORLD_CHAT");
                sides[1] = landing(f.host, "chat:PRIVATE_CHAT");
            });
            float progress = shipped[0];
            Landing from = sides[0];
            Landing to = sides[1];
            assertEquals(-travel * progress, from.dx(), 1.0e-3F,
                    "the leaving page rides -travel * progress");
            assertEquals(travel * (1.0F - progress), to.dx(), 1.0e-3F,
                    "the arriving page rides the director's shared expression");
            assertEquals(0.0F, from.dy(), 0.0F);
            assertEquals(1.0F, from.scale(), 1.0e-6F, "a slide translates and never scales");
            assertTrue(frame.finished(), "a detail<->detail frame draws both bodies itself");
            assertFalse(frame.tailCloses(), "and leaves no save open for the screen");

            List<int[]> band = runs(f.surface);
            int[] left = span(band, 0);
            int[] right = span(band, 1);
            assertNotNull(left, "the leaving page must be on the frame: " + describe(band));
            assertNotNull(right, "the arriving page must be on the frame: " + describe(band));
            assertTrue(left[1] < right[0], "the leaving page is the left one: " + describe(band));
            assertEquals((int) PANEL_X, left[0],
                    "the leaving page is clipped at the panel's left edge, not the surface's");
            assertNear((int) (PANEL_X + to.dx()), right[0],
                    "the arriving page's first column is where its offset puts it");
            assertEquals(DETAIL_LAYOUT.list.w(), travel,
                    "the detail-to-detail slide travels the list width");
        }
    }

    /**
     * The pop is the same two pages with the roles swapped: the page being left now
     * carries the director's expression and travels to the <em>right</em>, while the
     * destination comes back from the left. The colours therefore sit on the
     * opposite sides from the push frame — the role contract with pixels behind it
     * for the first time.
     */
    @Test
    void aPopSwapsTheTwoRolesSoTheColoursMirror() {
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(PRIVATE, WORLD, true);
            float travel = DETAIL_LAYOUT.list.w();
            assertEquals(0.0F, f.director.pageNavDx(travel), 1.0e-6F,
                    "a pop starts with the leaving page still in place");
            slideTo(f.director, 0.15F);

            float[] shipped = new float[1];
            Landing[] sides = new Landing[2];
            PageTransitionComposite.NavFrame frame = f.compositeFrame("pop slide", 50L, () -> {
                shipped[0] = f.director.navSlideProgress();
                // Same as the push, mirrored: the composite paints the destination's
                // chat body itself and closes its own save.
                sides[0] = landing(f.host, "chat:PRIVATE_CHAT");
                sides[1] = landing(f.host, "chat:WORLD_CHAT");
            });
            float progress = shipped[0];
            Landing leaving = sides[0];
            Landing arriving = sides[1];
            assertEquals(-travel * (1.0F - progress), arriving.dx(), 1.0e-3F,
                    "and the destination comes back from the left on the mirror expression");
            assertTrue(frame.finished(), "a detail<->detail pop draws both bodies itself");
            assertFalse(frame.tailCloses(), "and leaves no save open for the screen");
            assertEquals(travel * progress, f.director.pageNavDx(travel), 1.0e-3F,
                    "which is the director's pop expression at the shipped progress");
            assertTrue(arriving.dx() < 0.0F, "the destination is still off to the left");

            List<int[]> band = runs(f.surface);
            int[] cyan = span(band, 1);
            int[] red = span(band, 0);
            assertNotNull(cyan, "the pop's leaving page is on the frame: " + describe(band));
            assertNotNull(red, "the pop's destination is on the frame: " + describe(band));
            assertTrue(red[0] < cyan[0],
                    "the roles are mirrored: the destination is the left one now: " + describe(band));
            assertEquals((int) PANEL_X, red[0],
                    "the destination fills the panel from its left edge: " + describe(band));
            // The destination's offset is what put it there: it has come in from the left
            // far enough to cover the panel edge, so the red run starts at the panel and
            // its own offset is still the negative one the composite asked for.
            assertTrue(arriving.dx() < 0.0F, "the destination rides a negative offset: " + arriving.dx());
            assertTrue(PANEL_X + arriving.dx() < PANEL_X,
                    "so its own first column is left of the panel edge, i.e. clipped");
        }
    }

    // ------------------------------------------------------------ 3. the zoom

    /**
     * The detail-to-detail zoom is serial: EXIT paints only the page being left (the
     * arriving side opens no layer and draws nothing) and ENTER paints only the
     * arriving one, so the two colours appear on different frames and are never
     * stacked into a double exposure.
     */
    @Test
    void theZoomPaintsOneSidePerPhaseAndNeverBothInAFrame() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(WORLD, PRIVATE, false);
            assertTrue(f.director.zoomExiting(), "a zoom starts on the leaving page");

            List<int[]> exitBand = new ArrayList<>();
            // No clock step in this first frame: EXIT starts at alpha 1.0 / scale 1.0, so
            // the frame that shows it is the one that does not advance the clock.
            PageTransitionComposite.NavFrame exit = f.compositeFrame("zoom exit", 0L,
                    () -> exitBand.addAll(runs(f.surface)));
            assertEquals(1, f.host.landings.size(), "EXIT composites one side only: "
                    + f.host.landings);
            Landing exitLanding = f.host.landings.get(0);
            // (This is the frame's first and only landings entry: after the first frame
            // tears its layer down, the probe's matrix is no longer the nav's, so reading
            // scale off a later entry would measure the teardown, not the phase.)
            assertEquals("chat:WORLD_CHAT", exitLanding.what());
            assertEquals(1.0F, exitLanding.scale(), 1.0e-6F,
                    "EXIT starts at scale 1.0 and shrinks from there");
            assertNotNull(span(exitBand, 0), "the leaving page is on the EXIT frame: "
                    + describe(exitBand));
            assertNull(span(exitBand, 1), "the arriving page is not: " + describe(exitBand));
            assertTrue(exit.finished(), "the composite finished this frame itself");
            assertFalse(exit.movingHidden(), "EXIT is the visible phase on this pair");

            // Mid-EXIT: still one side, now shrinking toward 0.9 and fading.
            f.director.stepZoomNav(50.0F);
            List<int[]> midBand = new ArrayList<>();
            f.compositeFrame("zoom exit mid", 50L, () -> midBand.addAll(runs(f.surface)));
            assertEquals(2, f.host.landings.size(), "mid-EXIT is still one side: " + f.host.landings);
            Landing mid = f.host.landings.get(1);
            assertEquals("chat:WORLD_CHAT", mid.what());
            assertEquals(0.9F + f.director.navExitAlpha() * 0.1F, mid.scale(), 1.0e-6F,
                    "EXIT drains at 1.0 -> 0.9 around the panel middle");
            assertEquals(1, midBand.size(),
                    "one colour only on the mid-EXIT frame: " + describe(midBand)
                            + " alpha=" + f.director.navExitAlpha()
                            + " scale=" + mid.scale());

            // ENTER: the arriving page, and only it. The handover happens on the frame
            // itself (the clock is stepped inside `begin`), so the zoom is still in
            // flight when the frame composes — stepping it here instead would land the
            // nav before the frame and leave nothing to assert.
            stepToEnter(f.director);
            List<int[]> enterBand = new ArrayList<>();
            Landing[] enter = new Landing[1];
            float[] enterAlpha = new float[1];
            // The earlier frames' landings are still in the list; this frame owns its own.
            f.host.landings.clear();
            PageTransitionComposite.NavFrame enterFrame = f.compositeFrame("zoom enter", 50L, () -> {
                // ENTER is the visible phase of a push: the composite opens the moving
                // layer, paints the arriving page's whole body itself and closes both.
                enter[0] = landing(f.host, "chat:PRIVATE_CHAT");
                // Read while the frame is still composited: the clock's ENTER alpha is
                // what the scale was built from.
                enterAlpha[0] = f.director.navEnterAlpha();
                enterBand.addAll(runs(f.surface));
            });
            assertEquals("chat:PRIVATE_CHAT", enter[0].what(),
                    "ENTER composites one side only: " + f.host.landings);
            assertFalse(f.host.landings.stream().anyMatch(l -> l.what().equals("chat:WORLD_CHAT")),
                    "the leaving page is not composited in ENTER: " + f.host.landings);
            assertEquals(1.1F - enterAlpha[0] * 0.1F, enter[0].scale(), 1.0e-6F,
                    "ENTER settles at 1.1 -> 1.0");
            assertNotNull(span(enterBand, 1), "the arriving page is on the ENTER frame: "
                    + describe(enterBand));
            assertNull(span(enterBand, 0), "the leaving page is not: " + describe(enterBand));
            assertTrue(enterFrame.finished(), "the composite paints the arriving body itself");
            assertFalse(enterFrame.tailCloses(), "and leaves no save open for the screen");
            assertFalse(enterFrame.movingHidden(), "ENTER draws the arriving page");
        }
    }

    // ------------------------------------ 4. the root <-> detail composite

    /**
     * The root&lt;-&gt;detail push: the root screen is the bottom layer and the moving
     * page rides the director's slide expression over it, both clipped to the panel.
     * Unlike the zoom, this path composites both sides in one frame.
     */
    @Test
    void aRootToDetailPushCompositesBothLayersInOneFrame() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.WORLD_CHAT;
            // The root body is painted first and the moving chat page lands on top of
            // it, so only the chat side's colour is scanned on this path.
            f.host.recordRootPixels = false;
            f.director.startPageNav(NavPage.of(AppPage.CHAT_LIST), WORLD, false);
            // Far enough in that the arriving page has entered the frame: it is a whole
            // panel wide, so early in the slide it is still off-canvas entirely. Tiny
            // steps, and the loop stops as soon as the page is in — a frame that lands
            // the nav early would leave nothing to measure.
            float travel = DETAIL_LAYOUT.rect().w();
            // A whole panel wide, so the arriving page is off-canvas until the slide has
            // brought it well in. The scan band is the middle row of a surface that is
            // narrower than the panel, so a page whose first column sits past the
            // surface's right edge has no pixels to read at all: the loop stops only
            // once the arriving page is on the band, not merely "less than a panel out".
            int bandRight = SURFACE_W - EDGE_TOLERANCE * 2;
            for (int i = 0; i < 5000 && f.director.pageNavActive()
                    && PANEL_X + travel * (1.0F - f.director.navSlideProgress()) > bandRight; i++) {
                f.director.stepSlideNav(1.0F);
            }
            assertTrue(f.director.pageNavActive(),
                    "the nav must still be open, at " + f.director.navSlideProgress());

            Landing[] sides = new Landing[2];
            List<int[]> band = new ArrayList<>();
            float[] shipped = new float[1];
            PageTransitionComposite.NavFrame frame = f.compositeFrame("root to detail slide", 50L, () -> {
                shipped[0] = f.director.navSlideProgress();
                // The moving page's chat body is the screen's half of this frame too.
                f.drawArrivingBody(WORLD);
                sides[0] = landing(f.host, "root:CHAT_LIST");
                sides[1] = landing(f.host, "chat:WORLD_CHAT");
                band.addAll(runs(f.surface));
            });
            float progress = shipped[0];
            Landing root = sides[0];
            Landing moving = sides[1];
            assertTrue(frame.tailCloses(), "the chat body follows inside the frame's save");
            assertTrue(travel * (1.0F - progress) < PANEL_W,
                    "the fixture must have the arriving page on the canvas");
            assertEquals(-travel * progress, root.dx(), 1.0e-3F,
                    "the root page underneath rides -travel * progress");
            assertEquals(travel * (1.0F - progress), moving.dx(), 1.0e-3F,
                    "and the moving page the director's shared expression");
            assertEquals(1.0F, root.scale(), 1.0e-6F, "a slide never scales");

            // Only the moving page's colour is readable here: the root's chat body is the
            // same WORLD_CHAT page the moving layer carries, and the moving layer paints
            // over the whole strip the root occupies (see the class note on this path).
            // The root's own layer is evidenced by its landing above, not by pixels.
            int[] movingSpan = span(band, 0);
            assertNotNull(movingSpan, "the moving page is on the frame: " + describe(band));
            assertNull(span(band, 2), "the root's own probe is under the moving layer");
            assertNear((int) (PANEL_X + moving.dx()), movingSpan[0],
                    "the moving page's first column is where its offset puts it");
            assertFalse(f.host.headerSuppressed, "the tail restores the header flag");
        }
    }

    /**
     * The zoom's invisible phase on a root&lt;-&gt;detail push: on the EXIT half the
     * root is the side being left, so the moving page is invisible and the composite
     * reports {@code movingHidden} — the flag the screen uses to skip the chat body
     * and the emoji/context overlays with it. On the ENTER half the moving page is
     * the visible one and the root side opens no layer at all.
     */
    @Test
    void theRootToDetailZoomSkipsTheInvisibleSideAndReportsIt() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.WORLD_CHAT;
            f.director.startPageNav(NavPage.of(AppPage.CHAT_LIST), WORLD, false);
            assertTrue(f.director.zoomExiting());

            Landing[] exitRoot = new Landing[1];
            // No clock step in this frame: the first EXIT frame is alpha 1.0 at scale 1.0.
            PageTransitionComposite.NavFrame exit = f.compositeFrame("root zoom exit", 0L,
                    () -> exitRoot[0] = landing(f.host, "root:CHAT_LIST"));
            assertEquals("root:CHAT_LIST", exitRoot[0].what());
            assertFalse(f.host.landings.stream().anyMatch(l -> l.what().equals("chat:WORLD_CHAT")),
                    "the moving side is invisible during EXIT: " + f.host.landings);
            assertTrue(exit.running(), "the chat page leaves the frame open for the nav tail");
            assertTrue(exit.movingHidden(), "and the screen must skip its draws");
            assertFalse(exit.movingSlide(), "a zoom never opens the slide save");
            assertNull(exit.movingLayer(), "an invisible side opens no layer");

            // ENTER: the moving page is the visible one and the root side skips. The
            // handover runs inside the frame (the clock is stepped inside `begin`), so
            // the ENTER half is composed while the zoom is still in flight.
            stepToEnter(f.director);
            f.host.landings.clear();
            Landing[] enter = new Landing[1];
            PageTransitionComposite.NavFrame enterFrame = f.compositeFrame("root zoom enter", 50L, () -> {
                // The arriving chat body is the screen's half of this frame; the root side
                // is the invisible one, so nothing may draw it.
                f.drawArrivingBody(WORLD);
                enter[0] = landing(f.host, "chat:WORLD_CHAT");
            });
            assertEquals("chat:WORLD_CHAT", enter[0].what(),
                    "only the moving page draws now: " + f.host.landings);
            assertFalse(f.host.landings.stream().anyMatch(l -> l.what().startsWith("root:")),
                    "the root side is invisible during ENTER: " + f.host.landings);
            assertTrue(enterFrame.tailCloses(), "the arriving chat body is the screen's half");
            assertFalse(enterFrame.movingHidden(), "ENTER is the visible phase of a push");
            assertFalse(f.host.headerSuppressed, "the tail always restores the header flag");
        }
    }

    // --------------------------------- 5. the settings / profile early returns

    /**
     * A settings sub-page has no composer tail to paint into the moving layer, so
     * the composite renders it, closes its own layer and returns a finished frame
     * whose header names the <em>destination</em>. The drawer is the real
     * {@link SettingsDetailPage} here, so this is a full offscreen frame of a
     * push-to-settings.
     */
    @Test
    void theSettingsEarlyReturnRendersTheDrawerAndClosesItsLayer() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.SETTINGS_SECTION;
            f.director.startPageNav(NavPage.of(AppPage.SETTINGS), SETTINGS_SECTION, false);
            slideTo(f.director, 0.15F);

            PageTransitionComposite.NavFrame frame = f.beginFrame("settings early return");

            assertTrue(frame.finished(), "the settings branch completes the frame itself");
            assertFalse(frame.running(), "so the screen returns without the nav tail");
            assertEquals(f.baseline, f.canvas.getSaveCount(),
                    "the layer the branch opened was closed before the drawer ran");
            assertFalse(f.host.headerSuppressed,
                    "the branch hands the fixed header back before drawing it");
            assertTrue(f.host.calls.contains("drawBezel:detail"),
                    "the bezel takes the detail layout this branch owns: " + f.host.calls);
            assertEquals(1, f.host.landings.size(),
                    "only the root side is composited: " + f.host.landings);
            assertTrue(f.detailScrollReads > 0,
                    "the settings drawer ran: it reads the detail scroll it paints through");
            // The frame carries pixels, and the drawer's own chrome is on it — the
            // header this branch draws is real, not a call that went nowhere.
            assertNotNull(drawnBounds(f.surface), "the branch painted nothing at all");
        }
    }

    /**
     * The same shape for the profile detail branch, whose body is the one-line
     * forward to {@code ProfilePageWiring} — the real page renders here.
     */
    @Test
    void theProfileDetailEarlyReturnReachesItsDrawerAndPaintsTheFixedHeader() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.PROFILE_DETAIL;
            f.host.profileIsSentinel = true;
            f.director.startPageNav(NavPage.of(AppPage.SETTINGS), PROFILE_DETAIL, false);
            slideTo(f.director, 0.15F);

            // The real page cannot render offscreen (it reads the local player through
            // the client), so the drawer is a sentinel here: reaching it is the fact
            // this test owns, and it is asserted where the composite stops.
            AssertionError thrown = assertThrows(AssertionError.class,
                    () -> f.beginFrame("profile early return"));
            assertTrue(thrown.getMessage().contains("profile sentinel"),
                    "the composite must have reached the profile drawer: " + thrown.getMessage());

            // The frame stopped at that point, but the composite's own layer pairing and
            // the fixed header are already visible: the pushed header is painted into the
            // detail layout's header band, and the moving page's save was closed first.
            assertTrue(f.host.calls.contains("suppressHeader:true"),
                    "the transition suppresses the settled header first: " + f.host.calls);
            assertEquals(f.baseline + 1 + f.saveDepth(), f.canvas.getSaveCount(),
                    "the branch must have closed its own layer before it delegated");
            assertNotNull(drawnIn(f.surface, DETAIL_LAYOUT.header),
                    "the fixed pushed header must be painted into the detail header band");
            assertNotNull(drawnIn(f.surface, DETAIL_LAYOUT.header),
                    "the fixed pushed header is painted into the detail layout's band");
        }
    }

    /**
     * The zoom's invisible phase reaches the settings branch too: on the EXIT half of
     * a push the moving page is the one being left, so nothing may draw it — the gate
     * is what keeps an invisible page from compositing straight onto the visible side.
     */
    @Test
    void anInvisibleZoomSideDrawsNothingAndStillPairsItsLayer() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.SETTINGS_SECTION;
            f.director.startPageNav(NavPage.of(AppPage.CHAT_LIST), SETTINGS_SECTION, false);
            assertTrue(f.director.zoomExiting(), "the moving side is the invisible one on this half");

            PageTransitionComposite.NavFrame frame = f.beginFrame("invisible settings side");

            assertTrue(frame.finished());
            assertNull(frame.movingLayer(), "an invisible side opens no layer");
            assertFalse(frame.movingSlide());
            assertEquals(f.baseline, f.canvas.getSaveCount(),
                    "and it must not pop a save it never opened");
            assertFalse(f.host.headerSuppressed);
        }
    }

    // ----------------------------------------------------------- 6. the settle

    /**
     * The settled early return: with the clock idle the composite opens no layer,
     * paints nothing and reports {@code none()}, so the screen draws its settled
     * frame. The save count is the evidence — a "harmless" layer opened and closed
     * here would still be pure overdraw on every single settled frame.
     */
    @Test
    void aSettledClockOpensNothingAndPaintsNothing() {
        try (Fixture f = new Fixture()) {
            int before = f.canvas.getSaveCount();
            PageTransitionComposite.NavFrame frame = f.composite.begin(
                    f.canvas, DETAIL_LAYOUT, 0, 0, 0.0F, 0.0F, 50L);

            assertEquals(new PageTransitionComposite.NavFrame(null, false, false, false, false, false), frame,
                    "an idle clock is no nav at all");
            assertFalse(frame.running(), "so the screen draws its settled frame and never calls end");
            assertFalse(frame.tailCloses(), "and there is no save-point to close either");
            assertFalse(frame.finished());
            assertEquals(before, f.canvas.getSaveCount(), "no layer, no save: pure overdraw skipped");
            assertEquals(List.of(), f.host.landings, "and nothing was painted");
            assertEquals(List.of(), f.nav.calls, "and no clock was stepped");
        }
    }

    /**
     * A nav that lands <em>inside</em> the step: the composite finishes it, applies the
     * finished pop through the director, draws the arriving root page and returns a
     * finished frame, so the screen returns without painting the settled frame a second
     * time. The clock's per-frame step is capped — a long frame may not teleport a nav —
     * so the landing is reached frame by frame and asserted on the frame that carries it.
     */
    @Test
    void aNavThatLandsMidFrameFinishesTheRootFrameItself() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.CHAT_LIST;
            f.director.startPageNav(WORLD, NavPage.of(AppPage.CHAT_LIST), true);
            // Short of settled: the landing frame is the one this test owns, and these
            // small steps can never reach the band on their own.
            for (int i = 0; i < 5000 && f.director.navSlideProgress() < 0.88F; i++) {
                f.director.stepSlideNav(8.0F);
            }
            assertTrue(f.director.pageNavActive(),
                    "the nav must still be open, at " + f.director.navSlideProgress());
            f.host.landings.clear();

            // Frames until one carries the clock over the snap band; that frame is the
            // one this test owns. Every earlier frame is an ordinary running frame and
            // is closed through the tail.
            PageTransitionComposite.NavFrame frame = null;
            for (int i = 0; i < 12 && f.director.pageNavActive(); i++) {
                f.host.landings.clear();
                frame = f.beginFrame("landing frame " + i, 50L);
                if (frame.finished()) {
                    break;
                }
                f.endInFrame("pre-landing frame " + i, frame);
            }
            assertNotNull(frame, "the nav never produced a frame");
            assertTrue(frame.finished(), "the clock landed inside the frame: " + frame);
            assertFalse(frame.running(), "and there is no nav tail left to run");
            assertFalse(f.director.pageNavActive(), "the nav is over");
            assertEquals("root:CHAT_LIST", landing(f.host, "root:CHAT_LIST").what(),
                    "the arriving root page is painted from the composite");
            assertEquals(List.of("persistCurrentDraft", "captureReplySlot", "applyPendingPop"),
                    f.nav.calls, "the finished pop is applied exactly once, through the director");
            assertEquals(f.baseline, f.canvas.getSaveCount(), "with nothing left open");
        }
    }

    /**
     * A nav that lands on a <b>non-root</b> page must hand the frame back to the
     * screen, not claim it.
     *
     * <p>The screen returns as soon as a frame reports {@code finished()}, so a
     * settle that answered "done" on a detail destination painted the panel base
     * and the banner and then skipped the body, the header and the bezel — one
     * blank frame per navigation, and with decorative motion off (tau 0) it is the
     * <em>first</em> frame of every push. The landing arm therefore has to report
     * {@code noNav()} (neither running nor finished) for a non-root top page,
     * exactly as the pre-nav code did by falling out of its nav branch.</p>
     *
     * <p><b>Frame sequence, not the shell's early return.</b> The shell's
     * {@code if (frame.finished()) return;} is deliberately NOT replayed here: a
     * test that copies it reproduces the defect and stays green through it.</p>
     */
    @Test
    void aNavLandingOnANonRootPageHandsTheFrameBackToTheScreen() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.PRIVATE_CHAT;
            f.director.startPageNav(WORLD, PRIVATE, false);
            f.host.landings.clear();
            f.host.calls.clear();

            // One frame whose step carries the clock over the snap threshold. The
            // per-frame step is capped, so the landing is reached the way the game
            // reaches it fastest: with decorative motion off the tau is 0 and the
            // progress snaps to settled inside this very frame — the first frame of
            // the navigation, and the reason a "done" here is a visible blank flash
            // rather than a one-frame artefact nobody sees.
            assertTrue(f.director.pageNavActive(), "precondition: the nav is open");
            AtomChatConfig.get().animationEnabled = false;
            PageTransitionComposite.NavFrame frame = f.beginFrame("landing on a detail page", 50L);
            AtomChatConfig.get().animationEnabled = true;   // the restore @AfterEach also does
            assertFalse(f.director.pageNavActive(),
                    "precondition: this frame carried the clock to settled");

            assertFalse(frame.finished(),
                    "a settle on a non-root page must NOT claim the frame: the screen returns on"
                            + " finished(), which would skip the body, the header and the bezel");
            assertFalse(frame.running(), "there is no nav tail left to close either");
            assertFalse(frame.tailCloses(), "and the composite opened nothing to close");
            assertTrue(f.host.landings.isEmpty(),
                    "the composite paints neither a page body nor a root screen here: "
                            + f.host.landings);
            assertFalse(f.host.calls.stream().anyMatch(c -> c.startsWith("drawBezel")),
                    "and it must not paint the bezel itself: " + f.host.calls);
            assertEquals(f.baseline, f.canvas.getSaveCount(), "with nothing left open");

            // The screen's half of that frame, which the shell now reaches: the body it
            // paints is the one the navigation landed on.
            f.drawArrivingBody(PRIVATE);
            assertEquals("chat:PRIVATE_CHAT", f.host.landings.get(0).what(),
                    "the non-root landing frame is the screen's to paint: " + f.host.landings);
        }
    }

    /**
     * The other arm of the same settle: a <b>root</b> destination is painted by
     * the composite, which then owns the frame and says so — the screen returns
     * without repainting the settled root page a second time.
     */
    @Test
    void aNavLandingOnARootPageIsStillCompletedByTheComposite() {
        try (Fixture f = new Fixture()) {
            f.host.top = AppPage.CHAT_LIST;
            f.director.startPageNav(PRIVATE, NavPage.of(AppPage.CHAT_LIST), true);
            f.host.landings.clear();
            f.host.calls.clear();

            AtomChatConfig.get().animationEnabled = false;
            PageTransitionComposite.NavFrame frame = f.beginFrame("landing on a root page", 50L);
            AtomChatConfig.get().animationEnabled = true;

            assertTrue(frame.finished(),
                    "a root landing is finished here: the composite painted the whole frame");
            assertFalse(frame.running(), "and there is no nav tail left to run");
            assertFalse(f.director.pageNavActive(), "the nav is over");
            assertEquals(1, f.host.landings.size(),
                    "the arriving root page is painted once, by the composite: " + f.host.landings);
            assertEquals("root:CHAT_LIST", f.host.landings.get(0).what());
            assertTrue(f.host.calls.stream().anyMatch(c -> c.startsWith("drawBezel")),
                    "the root frame's bezel is the composite's to close: " + f.host.calls);
            assertEquals(f.baseline, f.canvas.getSaveCount(), "with nothing left open");
        }
    }

    /**
     * A slide that keeps running leaves exactly one save-point open and reports the
     * handle the tail needs: the plain save on a slide, the layer on a zoom —
     * {@code end} pairs its restore with that answer rather than guessing.
     */
    @Test
    void aRunningSlideLeavesThePlainSaveOpenForTheTail() {
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(NavPage.of(AppPage.CHAT_LIST), WORLD, false);
            slideTo(f.director, 0.15F);

            PageTransitionComposite.NavFrame frame = f.beginFrame("running slide");

            assertTrue(frame.running());
            assertFalse(frame.finished());
            assertTrue(frame.movingSlide(), "a slide opens the plain save and says so");
            assertNull(frame.movingLayer(), "and no layer");
            assertFalse(frame.movingHidden(), "a slide is never the hidden phase");

            f.frame("nav tail", () -> f.composite.end(f.canvas, DETAIL_LAYOUT, frame));
            assertFalse(f.host.headerSuppressed,
                    "the tail restores the header flag the branches set");
            assertEquals(f.baseline, f.canvas.getSaveCount(), "and pops the one save it was told about");
        }
    }

    /** The tail's root-destination arm: the header comes from the root layout. */
    @Test
    void theNavTailPicksTheHeaderFromTheDestinationsOwnLayout() {
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(WORLD, NavPage.of(AppPage.CHAT_LIST), true);
            slideTo(f.director, 0.15F);

            PageTransitionComposite.NavFrame frame = f.beginFrame("tail to root");
            f.frame("nav tail", () -> f.composite.end(f.canvas, DETAIL_LAYOUT, frame));

            assertEquals(f.baseline, f.canvas.getSaveCount(),
                    "the tail must pop exactly the save the composite opened");
            assertFalse(f.host.headerSuppressed);
        }
    }

    // --------------------------------------------------------------- the frame

    /**
     * The whole settle path of a real nav, frame by frame on one surface: every
     * frame balances the canvas. This is the multi-frame shape that catches "a
     * native object was released while a later frame still reaches it".
     */
    @Test
    void aWholeSlideNavSettlesFrameByFrameWithABalancedCanvas() {
        try (Fixture f = new Fixture()) {
            f.director.startPageNav(WORLD, PRIVATE, false);
            int frames = 0;
            while (f.director.pageNavActive() && frames < 40) {
                frames++;
                f.frame("nav frame " + frames, () -> {
                    PageTransitionComposite.NavFrame frame = f.composite.begin(
                            f.canvas, DETAIL_LAYOUT, 0, 0, 0.0F, 0.0F, 50L);
                    if (frame.finished()) {
                        return;
                    }
                    if (frame.running()) {
                        // The chat page falls through to the shell's settled draws;
                        // this test owns the composite half of the frame, so it closes
                        // the moving layer and leaves the rest to the shell's own guard.
                        f.composite.end(f.canvas, DETAIL_LAYOUT, frame);
                    }
                });
            }
            assertEquals(10, frames, "1 - e^(-0.714n) enters the snap band on the tenth frame");
            assertFalse(f.director.pageNavActive());
            assertFalse(f.host.headerSuppressed, "the last frame leaves the header restored");
        }
    }
}
