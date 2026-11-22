package com.atom.chat.config;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.settings.SettingsCatalog;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.frontend.checker.ScenarioCheck.Phase;
import com.atom.lib.frontend.probe.FixedHeaderClockAgent;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Surface;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Production About row rendering and controller input on the deterministic software surface. */
final class AboutSettingsReplay {
    private static final SettingsSection SECTION = SettingsSection.ABOUT;
    private static final String HEADER = "atomchat.settings.about.thirdparty.group";
    private static final URI FIRST_LINK = URI.create("https://github.com/HumbleUI/Skija");
    private static final URI LAST_LINK = URI.create("https://github.com/btzy/nativefiledialog-extended");
    private static final int IMMEDIATE = 235;
    private static final Set<Integer> SAMPLES = Set.of(34, 38, 60, 61, 66, 94, 95,
            101, 104, 114, 128, 142, 156, 157, 158, 172, 173, 174, 186,
            201, 206, 234, 235, 236, 237, 238, 239, 240);

    private final SettingsSectionPage settings = new SettingsSectionPage();
    private final ScrollController vertical = new ScrollController(false);
    private final UiLayout layout;
    private final Object strip;
    private final Method rowRect;
    private final Method logoRect;
    private final Method stripMax;
    private final Method stripItemX;
    private float mx = -100F;
    private float my = -100F;
    private int route;
    private int tick;
    private int linkCount;
    private float wheelStartPageY;
    private long startingInk;
    private float maxOffset;
    private float dragOrigin;

    private AboutSettingsReplay(int width) throws Exception {
        SettingsSectionPage.clearTranslationCache();
        layout = UiLayout.ofDetail(20, 30, width, 820);
        strip = field(settings, "creditsStrip");
        rowRect = method(SettingsSectionPage.class, "rowRect", SettingsSection.class, List.class,
                int.class, float.class, UiLayout.class);
        stripMax = method(SettingsSectionPage.class, "stripMaxScroll", strip.getClass(), UiLayout.Rect.class);
        stripItemX = method(SettingsSectionPage.class, "stripCardX", UiLayout.Rect.class, int.class,
                float.class, float.class);
        var cards = Class.forName("com.atom.chat.settings.AboutCreditCards");
        logoRect = method(cards, "logoRect", float.class, float.class);
        FixedHeaderClockAgent.requireAboutHooks();
    }

    static void run(Path out, int width, String locale, boolean light, SettingsContractScene capture) throws Exception {
        var scene = new AboutSettingsReplay(width);
        var cfg = AtomChatConfig.get();
        cfg.performanceMode = false;
        cfg.feedbackSoundEnabled = false;
        scene.settings.markEntered(clock(0));
        try (var surface = Surface.makeRasterN32Premul(width + 40, 900)) {
            for (int step = 0; step <= 240; step++) {
                final int tick = step;
                System.setProperty("fcl.historyCaretMillis", Long.toString(clock(tick)));
                UiStagger.atFrame(clock(tick), () -> {
                    try {
                        scene.frame(tick, surface);
                        if (SAMPLES.contains(tick)) scene.record(out, tick, surface, capture);
                    } catch (Exception e) {
                        throw new IllegalStateException("FCL_ABOUT frame=" + tick + " width=" + width, e);
                    }
                });
            }
        }
        System.out.println("FCL_ABOUT_SCENE width=" + width + " locale=" + locale + " light=" + light
                + " samples=" + SAMPLES.size() + " links=" + scene.linkCount);
    }

    private void frame(int tick, Surface surface) throws Exception {
        this.tick = tick;
        route = 0;
        FixedHeaderClockAgent.captureAboutFrame();
        measure();
        vertical.updateAnimation(clock(tick));
        if (tick == 0 || tick == 236) vertical.scrollToBottom(false);
        input(tick);
        measure();
        var canvas = surface.getCanvas();
        int saved = canvas.getSaveCount();
        canvas.clear(0xFF64767B);
        var cfg = AtomChatConfig.get();
        CornerGeometry.drawRoundedRect(canvas, layout.panelX, layout.panelY, layout.panelW, layout.panelH,
                UiTokens.panelRadius(com.atom.chat.ui.AtomChatAppearance.theme()), cfg.panelBgColor);
        settings.render(canvas, layout, SECTION, mx, my, vertical.getScrollY(), cfg.accentColor);
        require(canvas.getSaveCount() == saved, "balanced canvas");
        check(tick);
        linkCount += FixedHeaderClockAgent.aboutLinks().size();
    }

    private void measure() {
        vertical.setDecorativeMotion(!AtomChatConfig.get().performanceMode);
        vertical.setContent(settings.measureContent(layout, SECTION), layout.list.h());
    }

    private void input(int tick) throws Exception {
        switch (tick) {
            case 35 -> point(logo(0));
            case 62, 239 -> {
                point(logo(0));
                require(settings.themesPress(mx, my, layout, SECTION, vertical.getScrollY()), "logo press captured");
            }
            case 95 -> {
                mx = my = -100F;
                settings.endThemeStrip(mx, my, layout);
            }
            case 240 -> dragAndRelease(UiTokens.s(34));
            case 101 -> {
                point(logo(0));
                wheelStartPageY = vertical.getScrollY();
                maxOffset = (float) stripMax.invoke(settings, strip, row(creditsIndex()));
                require(maxOffset > UiTokens.s(60), "strip has horizontal overflow");
                wheel(0, -1, 1);
            }
            case 115, 129, 237 -> wheel(0, -100, 1);
            case 143, 157, 159, 238 -> wheel(100, 0, 1);
            case 158 -> dragAcrossGap();
            case 172 -> tap(logo(0));
            case 173 -> {
                UiLayout.Rect mark = logo(0);
                mx = mark.x() + mark.w() / 2;
                my = mark.bottom() + UiTokens.s(11);
                tap();
            }
            case 174, 187 -> {
                point(row(labelIndex()));
                wheel(0, tick == 174 ? 1 : -100, 2);
                mx = my = -100F;
            }
            case 202, IMMEDIATE -> {
                if (tick == IMMEDIATE) AtomChatConfig.get().performanceMode = true;
                fold();
            }
            case 236 -> {
                point(logo(0));
                wheel(0, -1, 1);
                require(offset() > 0 && near(offset(), target()) && !animating(), "performance wheel settles at input");
            }
            default -> { }
        }
    }

    private void check(int tick) throws Exception {
        List<Integer> ink = FixedHeaderClockAgent.aboutInk();
        int themeInk = 0xDC000000 | (AtomChatConfig.get().textSecondaryColor & 0xFFFFFF);
        require(ink.stream().allMatch(value -> value == themeInk), "project title uses constant secondary ink");
        List<URI> links = FixedHeaderClockAgent.aboutLinks();
        require(links.equals(tick == 172 ? List.of(FIRST_LINK) : List.of()), "only the logo tap requests its original URI: " + links);
        switch (tick) {
            case 34 -> {
                require(!ink.isEmpty() && near(scale(), 1F), "credits initially visible and at rest");
                startingInk = Integer.toUnsignedLong(ink.get(0));
            }
            case 38 -> require(!ink.isEmpty() && scale() > 1F
                    && Integer.toUnsignedLong(ink.get(0)) == startingInk, "hover raises shared card without changing title ink");
            case 66 -> require(scale() < 1F, "press uses the shared group card spring");
            case 95 -> require(!settings.isDraggingThemeStrip(), "release clears strip capture");
            case 101 -> require(near(offset(), 0F) && target() > 0F && animating(), "ordinary wheel selects strip and starts animation");
            case 104 -> require(offset() > 0F && offset() < target(), "ordinary wheel intermediate frame");
            case 114 -> require(near(offset(), UiTokens.s(60)) && near(vertical.getScrollY(), wheelStartPageY),
                    "one ordinary notch moves strip without scrolling page");
            case 128, 142, 237 -> {
                require(near(offset(), maxOffset) && near(offset(), target()), "last boundary clamps");
                var mark = logo(SettingsCatalog.thirdPartyRows().size() - 1);
                var hit = settings.hit(mark.x() + mark.w() / 2, mark.y() + mark.h() / 2, layout, SECTION, vertical.getScrollY());
                require(hit != null && hit.row().info() != null && LAST_LINK.toString().equals(hit.row().info().uri()),
                        "last visible logo retains its own link");
            }
            case 156, 157, 238 -> require(near(offset(), 0F) && near(target(), 0F), "first boundary clamps");
            case 158, 240 -> require(offset() > dragOrigin && !settings.isDraggingThemeStrip(),
                    "drag moves strip and releases");
            case 172 -> require(near(offset(), 0F), "actual first logo returns before click");
            case 186 -> require(vertical.getScrollY() < wheelStartPageY && near(offset(), 0F), "wheel outside strip goes to page only");
            case 206 -> require(foldExpansion() > 0F && foldExpansion() < 1F, "normal fold advances gradually");
            case 234 -> require(near(foldExpansion(), 0F) && ink.isEmpty(), "closed card stops painting");
            case IMMEDIATE -> require(near(foldExpansion(), 1F), "performance opens the fold on the same frame");
            case 236, 239 -> require(near(scale(), 1F) && !animating(), "performance card and strip have no in-flight motion");
            default -> { }
        }
        if (tick >= IMMEDIATE) require(!com.atom.chat.ui.Animations.enabled(), "performance motion gate disabled");
    }

    private void dragAcrossGap() throws Exception {
        UiLayout.Rect left = logo(0), right = logo(1);
        mx = (left.right() + right.x()) / 2;
        my = left.y() + left.h() / 2;
        require(settings.hit(mx, my, layout, SECTION, vertical.getScrollY()) == null, "drag starts outside link hitboxes");
        require(settings.themesPress(mx, my, layout, SECTION, vertical.getScrollY()), "viewport accepts drag from gap");
        dragAndRelease(UiTokens.s(52));
    }

    private void dragAndRelease(float distance) throws Exception {
        dragOrigin = offset();
        mx -= distance;
        settings.dragThemeStrip(mx, my, settings.themesRowRect(layout, SECTION, vertical.getScrollY()));
        settings.endThemeStrip(mx, my, layout);
    }

    private void fold() throws Exception {
        point(row(labelIndex()));
        var hit = settings.hit(mx, my, layout, SECTION, vertical.getScrollY());
        require(hit != null && HEADER.equals(hit.row().labelKey()) && settings.perform(hit), "fold uses its displayed header hit");
    }

    private void tap(UiLayout.Rect box) {
        point(box);
        tap();
    }

    private void tap() {
        require(settings.themesPress(mx, my, layout, SECTION, vertical.getScrollY()), "strip accepts tap");
        settings.endThemeStrip(mx, my, layout);
    }

    // Same handoff as Screen: let the content decide strip ownership before the page scroll sees the wheel.
    private void wheel(float horizontal, float amount, int expectedRoute) {
        boolean handled = settings.scrollCardStrip(mx, my, layout, SECTION, vertical.getScrollY(), horizontal, amount);
        if (handled) route = 1;
        else if (layout.list.contains(mx, my)) {
            vertical.wheel(amount);
            route = 2;
        }
        require(route == expectedRoute, "wheel routing " + route + " expected " + expectedRoute);
    }

    private void record(Path out, int tick, Surface surface, SettingsContractScene capture) throws Exception {
        Map<String, Double> observed = new TreeMap<>();
        var ink = FixedHeaderClockAgent.aboutInk();
        observed.put("caption.argb", ink.isEmpty() ? -1d : (double) Integer.toUnsignedLong(ink.get(0)));
        observed.put("card.scale", (double) scale());
        observed.put("fold.progress", (double) foldExpansion());
        observed.put("scroll.x", (double) offset());
        observed.put("scroll.xTarget", (double) target());
        observed.put("scroll.xMax", (double) maxOffset);
        observed.put("scroll.animating", animating() ? 1d : 0d);
        observed.put("scroll.y", (double) vertical.getScrollY());
        observed.put("scroll.yTarget", (double) vertical.getTarget());
        observed.put("wheel.route", (double) route);
        observed.put("actions.committed", (double) FixedHeaderClockAgent.aboutLinks().size());
        observed.put("link.total", (double) linkCount);
        if (tick == 34 || tick == 38 || tick == 60) observed.put("motion.HOVER", progress(scale() - 1F, travel()));
        if (tick == 61 || tick == 66 || tick == 94) observed.put("motion.PRESS", progress(1F - scale(), travel()));
        if (tick == 201 || tick == 206 || tick == 234) observed.put("motion.TRANSITION", progress(1F - foldExpansion(), 1F));
        Phase phase = switch (tick) {
            case 34, 61, 101, 201 -> Phase.INITIAL;
            case 38, 66, 104, 206 -> Phase.INTERMEDIATE;
            case 95, 158, 173, 240 -> Phase.CANCELLED;
            case 156, 238 -> Phase.REVERSED;
            default -> Phase.SETTLED;
        };
        if (capture != null) {
            capture.recordAbout(tick, surface, phase, tick >= IMMEDIATE ? "creditsImmediate" : "creditsInput", observed);
        } else {
            try (var image = surface.makeImageSnapshot(); var png = image.encodeToData(EncodedImageFormat.PNG)) {
                Files.write(out.resolve("frame-" + tick + ".png"), png.getBytes());
            }
            System.out.println("ABOUT_FRAME " + tick + " " + observed);
        }
    }

    private float travel() throws Exception {
        UiLayout.Rect card = row(creditsIndex());
        return Math.min(PressScale.BUDGET_PX / (Math.max(card.w(), card.h()) / 2F), PressScale.BUDGET_PCT);
    }

    private static double progress(float current, float distance) {
        float value = current / distance;
        if (value <= .001F) return 0d;
        return value >= .999F ? 1d : value;
    }

    private UiLayout.Rect logo(int index) throws Exception {
        UiLayout.Rect rect = row(creditsIndex());
        var cards = Class.forName("com.atom.chat.settings.AboutCreditCards");
        var width = cards.getDeclaredField("WIDTH");
        width.setAccessible(true);
        float x = (float) stripItemX.invoke(null, rect, index, offset(), width.getFloat(null));
        return (UiLayout.Rect) logoRect.invoke(null, x, rect.y());
    }

    private int labelIndex() {
        return indexOf(row -> HEADER.equals(row.labelKey()));
    }

    private int creditsIndex() {
        return indexOf(row -> row.kind().name().equals("CREDITS"));
    }

    private int indexOf(java.util.function.Predicate<SettingsSectionPage.Row> condition) {
        var rows = settings.rows(SECTION);
        for (int index = 0; index < rows.size(); index++) if (condition.test(rows.get(index))) return index;
        return -1;
    }

    private UiLayout.Rect row(int index) throws Exception {
        require(index >= 0, "visible credits or heading row");
        return (UiLayout.Rect) rowRect.invoke(settings, SECTION, settings.rows(SECTION), index,
                vertical.getScrollY(), layout);
    }

    private float foldExpansion() {
        return settings.tutorialFolds().expansion().getOrDefault("label:" + HEADER, 1F);
    }

    private float scale() throws Exception {
        PressScale scale = (PressScale) ((Map<?, ?>) field(settings, "aboutCardPress")).get(creditsIndex());
        return scale == null ? 1F : scale.scale();
    }

    private float offset() throws Exception { return (float) field(strip, "scroll"); }
    private float target() throws Exception { return (float) field(strip, "target"); }
    private boolean animating() throws Exception { return (boolean) field(strip, "animActive"); }

    private void point(UiLayout.Rect bounds) {
        mx = bounds.x() + bounds.w() / 2;
        my = bounds.y() + bounds.h() / 2;
    }

    private void require(boolean value, String message) {
        if (!value) throw new AssertionError("FCL_ABOUT frame=" + tick + " " + message);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < .01F; }
    private static long clock(int tick) { return 1000L + tick * 16L; }

    private static Method method(Class<?> type, String name, Class<?>... types) throws Exception {
        var method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Object owner, String name) throws Exception {
        return SettingsTrialReplay.field(owner.getClass(), owner, name);
    }

}
