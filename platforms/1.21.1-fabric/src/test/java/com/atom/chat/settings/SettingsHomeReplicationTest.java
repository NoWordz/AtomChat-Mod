package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.PreviewLanguage;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.font.FontId;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.font.FontSource;
import com.atom.lib.geometry.Rect;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.Constraints;
import com.atom.lib.layout.LeafMeasure;
import com.atom.lib.layout.LayoutNode;
import com.atom.lib.layout.LayoutSnapshot;
import com.atom.lib.layout.LayoutStyle;
import com.atom.lib.layout.LayoutTree;
import com.atom.lib.layout.Length;
import com.atom.lib.layout.MeasuredSize;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.text.TextRequest;
import com.atom.lib.text.TextService;
import com.google.gson.GsonBuilder;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direction B evidence fixture (AtomLib docs/next-directions.md): repaint the
 * real settings home page twice in one process — once through AtomChat's
 * shipping draw path ({@link SettingsHomePage}), once through the AtomLib stack
 * (Yoga layout + TextService + the shared design package) — and record how far
 * the two agree. Pass/fail lives in the emitted results.json, not in JUnit
 * assertions: this is a measurement, not a gate. Only sanity checks assert.
 *
 * <p>Both sides share the same bundled Noto bytes (read from this mod's
 * resources), the same design-package tokens and card painter, and the same
 * zh_cn strings, so every difference the fixture finds is a real stack
 * difference, not a fixture difference.</p>
 */
class SettingsHomeReplicationTest {
    private static final float PANEL_X = 40F;
    private static final float PANEL_Y = 20F;
    private static final float PANEL_W = 480F;
    private static final float PANEL_H = 860F;
    private static final int CANVAS_W = 560;
    private static final int CANVAS_H = 940;
    private static final int PIXEL_DELTA_LIMIT = 16;
    private static final float PIXEL_SMOKE_LIMIT = 0.02F;
    private static final Path OUTPUT = Path.of("build/test-artifacts/settings-home-replication");

    /** One recorded comparison outcome; the report reads these, not a JUnit pass/fail. */
    private record Check(String name, boolean pass, String detail) {}

    @Test void pixelSmokeDetectsOpaqueBlackAgainstWhite() {
        try (Surface black = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H);
             Surface white = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H)) {
            black.getCanvas().clear(0xFF000000);
            white.getCanvas().clear(0xFFFFFFFF);
            var result = pixelSmoke(black, white, List.of());
            assertEquals(526_400L, result.get("differingPixels"));
            assertEquals(526_400L, result.get("differingOutsideTextBoxes"));
            assertEquals(Map.of("whole", 1.0, "excludingText", 1.0), result.get("ratios"));
        }
    }

    @Test void pixelSmokeToleratesAdjacentChannelsAcrossTheSignedByteBoundary() {
        try (Surface lower = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H);
             Surface upper = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H)) {
            lower.getCanvas().clear(0xFF7F7F7F);
            upper.getCanvas().clear(0xFF808080);
            var result = pixelSmoke(lower, upper, List.of());
            assertEquals(0L, result.get("differingPixels"));
            assertEquals(0L, result.get("differingOutsideTextBoxes"));
            assertEquals(Map.of("whole", 0.0, "excludingText", 0.0), result.get("ratios"));
        }
    }

    @Test void replicateSettingsHomeWithTheAtomLibStack() throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        Object old = configField.get(null);
        var cfg = new AtomChatConfig();
        cfg.animationEnabled = false;
        configField.set(null, cfg);
        List<Check> checks = new ArrayList<>();
        try (var language = PreviewLanguage.install("zh_cn")) {
            assertNotNull(language);

            // ---- Ground truth: AtomChat's own settings home, offscreen. ----
            var layout = UiLayout.ofRoot(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, false);
            var home = new SettingsHomePage();
            var sections = SettingsSection.values();
            Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
            float acTextH = SkiaFontRenderer.textHeight(titleFont);
            String[] titles = new String[sections.length];
            for (int i = 0; i < sections.length; i++) {
                titles[i] = SettingsHomePage.title(sections[i]);
                checks.add(new Check("acTitle." + sections[i].id(), !titles[i].isBlank(), titles[i]));
            }

            try (Surface acSurface = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H)) {
                Canvas canvas = acSurface.getCanvas();
                canvas.clear(0);
                home.render(canvas, layout, -1F, -1F, 0F);
                assertEquals(1, canvas.getSaveCount());
                writePng(acSurface, "ac-settings-home.png");

                // ---- Replica: AtomLib Yoga + TextService + design package. ----
                byte[] regular = resourceBytes("/com/atom/lib/fonts/bundled-default.otf");
                byte[] bold = resourceBytes("/com/atom/lib/fonts/bundled-default-bold.otf");
                checks.add(new Check("fontFixtureBytes", regular != null && bold != null,
                        "regular=" + (regular == null ? -1 : regular.length) + "B bold="
                                + (bold == null ? -1 : bold.length) + "B (the same files FontManager loads)"));
                var runtime = AtomLibRuntime.open("settings-home-replication");
                var fonts = new FontRegistry(runtime);
                var heading = new FontId("rep.noto.bold");
                fonts.register(new FontId("rep.noto"), FontSource.bytes(regular));
                fonts.register(heading, FontSource.bytes(bold));
                fonts.setFallbacks(List.of());
                checks.add(new Check("libFontRegistrations", true,
                        "bundled.otf variation axes=" + variationCount(regular)
                                + " (FontManager clones it to wght 500; FontRegistry loads it as-is)"));
                try (TextService text = new TextService(fonts);
                     YogaLayoutEngine engine = new YogaLayoutEngine()) {

                    float inset = UiTokens.ROW_CLIP_INSET;
                    float gap = UiTokens.SETTINGS_TILE_GAP;
                    float topGap = UiTokens.ROOT_CONTENT_GAP;
                    var tree = new LayoutTree(gridTree(topGap, inset, gap,
                            layout.list.w(), layout.list.h()));
                    LayoutSnapshot snapshot = engine.layout(tree,
                            new Viewport(layout.list.w(), layout.list.h(), 1F));
                    // Rounding sensitivity: the same tree on a finer pixel grid.
                    // A fresh engine: relayouting the SAME engine at a different
                    // pixelsPerUnit is itself a known hazard, probed below.
                    LayoutSnapshot fine;
                    try (YogaLayoutEngine fineEngine = new YogaLayoutEngine()) {
                        fine = fineEngine.layout(tree,
                                new Viewport(layout.list.w(), layout.list.h(), 16F));
                    }
                    // Probe: changing pixelsPerUnit on a live engine. The
                    // locked adapter used to crash here on targets bundling
                    // lwjgl-yoga 3.3.3 (the YGNodeMarkDirtyAndPropogate/
                    // Propagate rename); fixed in AtomLib by generating the
                    // binding spelling per target. Still recorded as a
                    // measurement, not a JUnit gate, so older pinned inputs
                    // fail the check in results.json without failing CI.
                    String densityChange;
                    boolean densityOk;
                    try (YogaLayoutEngine probe = new YogaLayoutEngine()) {
                        probe.layout(tree, new Viewport(layout.list.w(), layout.list.h(), 1F));
                        try {
                            probe.layout(tree, new Viewport(layout.list.w(), layout.list.h(), 16F));
                            densityChange = "relayout at a new pixelsPerUnit: no error";
                            densityOk = true;
                        } catch (Throwable t) {
                            densityChange = "relayout at a new pixelsPerUnit threw " + t;
                            densityOk = false;
                        }
                    }
                    checks.add(new Check("yoga.pixelDensityChangeOnLiveEngine", densityOk, densityChange));

                    for (int i = 0; i < sections.length; i++) {
                        var ac = SettingsHomePage.tileRect(layout, i, 0F);
                        var lib = snapshot.box(new NodeId("tile" + i)).border();
                        var libFine = fine.box(new NodeId("tile" + i)).border();
                        float dx = Math.max(Math.abs(lib.x() + layout.list.x() - ac.x()),
                                Math.abs(libFine.x() + layout.list.x() - ac.x()));
                        float dy = Math.max(Math.abs(lib.y() + layout.list.y() - ac.y()),
                                Math.abs(libFine.y() + layout.list.y() - ac.y()));
                        float dw = Math.max(Math.abs(lib.width() - ac.w()),
                                Math.abs(libFine.width() - ac.w()));
                        float dh = Math.max(Math.abs(lib.height() - ac.h()),
                                Math.abs(libFine.height() - ac.h()));
                        float maxDelta = Math.max(Math.max(dx, dy), Math.max(dw, dh));
                        checks.add(new Check("tileGeometry." + sections[i].id(), maxDelta <= 0.51F,
                                String.format("ac=(%.3f,%.3f,%.3f,%.3f) lib@1u=(%.3f,%.3f,%.3f,%.3f) "
                                                + "lib@1/16u=(%.3f,%.3f,%.3f,%.3f) maxDelta=%.3f",
                                        ac.x(), ac.y(), ac.w(), ac.h(),
                                        lib.x() + layout.list.x(), lib.y() + layout.list.y(),
                                        lib.width(), lib.height(),
                                        libFine.x() + layout.list.x(), libFine.y() + layout.list.y(),
                                        libFine.width(), libFine.height(), maxDelta)));
                    }

                    // Text: typography geometry only — line count, line width,
                    // line height. No glyph pixels are compared.
                    // Probe first: why shaped widths differ from AC's
                    // per-glyph measure. Records the Font subpixel flag and
                    // both width paths for one glyph.
                    try (Font probeFont = fonts.openFont(heading, UiTokens.SETTINGS_TILE_TITLE, "");
                         var shaper = io.github.humbleui.skija.shaper.Shaper
                                 .makeShapeDontWrapOrReorder();
                         var shaped = shaper.shapeLine("外观", probeFont)) {
                        checks.add(new Check("textMetrics.subpixelProbe", true,
                                "Font.isSubpixel=" + probeFont.isSubpixel()
                                        + " measureTextWidth(\"外观\")=" + probeFont.measureTextWidth("外观")
                                        + " shapeLine(\"外观\").getWidth()=" + shaped.getWidth()));
                    }
                    for (int i = 0; i < sections.length; i++) {
                        var tile = SettingsHomePage.tileRect(layout, i, 0F);
                        float maxW = tile.w() - UiTokens.s(16);
                        var request = new TextRequest(titles[i], heading, UiTokens.SETTINGS_TILE_TITLE,
                                cfg.textPrimaryColor, 1, true);
                        var line = text.layout(request,
                                new Constraints(Constraints.Axis.atMost(maxW), Constraints.Axis.undefined()));
                        float acWidth = SkiaFontRenderer.getStringWidth(titleFont, titles[i]);
                        float widthDelta = Math.abs(line.size().width() - acWidth);
                        checks.add(new Check("titleWidth." + sections[i].id(), widthDelta <= 0.5F,
                                String.format("ac=%.3f lib=%.3f delta=%.3f lines=%d truncated=%s",
                                        acWidth, line.size().width(), widthDelta,
                                        line.lines().size(), line.truncated())));
                        float libLineH = line.lines().isEmpty() ? 0 : line.lines().get(0).height();
                        checks.add(new Check("titleLineHeight." + sections[i].id(), true,
                                String.format("acTextHeight=%.3f libLineHeight=%.3f delta=%.3f "
                                                + "(group centering reads textHeight, so glyph-label "
                                                + "gap shifts by half of the delta)",
                                        acTextH, libLineH, libLineH - acTextH)));
                    }

                    // Paint the replica with the shared design package + TextService.
                    try (Surface libSurface = Surface.makeRasterN32Premul(CANVAS_W, CANVAS_H)) {
                        Canvas libCanvas = libSurface.getCanvas();
                        libCanvas.clear(0);
                        libCanvas.save();
                        try {
                            SkiaDraw.clip(libCanvas, layout.list.x(), layout.list.y(),
                                    layout.list.w(), layout.list.h(), 0F);
                            libCanvas.translate(layout.list.x(), layout.list.y());
                            try (Font metricFont = fonts.openFont(heading,
                                    UiTokens.SETTINGS_TILE_TITLE, "")) {
                                float capHeight = metricFont.getMetrics().getCapHeight();
                                for (int i = 0; i < sections.length; i++) {
                                    var border = snapshot.box(new NodeId("tile" + i)).border();
                                    drawTileReplica(libCanvas, text, heading, sections[i], titles[i],
                                            border, cfg.textPrimaryColor, capHeight);
                                }
                            }
                        } finally {
                            libCanvas.restore();
                        }
                        writePng(libSurface, "lib-settings-home.png");

                        // Pixel smoke: whole-image ratio, plus the non-text ratio
                        // that carries the pass/fail threshold.
                        List<Rect> textBoxes = textBoxes(layout, snapshot);
                        Map<String, Object> pixels = pixelSmoke(acSurface, libSurface, textBoxes);
                        @SuppressWarnings("unchecked")
                        Map<String, Double> ratios = (Map<String, Double>) pixels.get("ratios");
                        double excludingText = ratios.get("excludingText");
                        checks.add(new Check("pixelSmoke.nonText", excludingText < PIXEL_SMOKE_LIMIT,
                                String.format("whole=%s excludingText=%s (limit %.2f, per-channel delta > %d)",
                                        ratios.get("whole"), ratios.get("excludingText"),
                                        PIXEL_SMOKE_LIMIT, PIXEL_DELTA_LIMIT)));

                        emitResults(checks, layout, pixels);
                    }
                } finally {
                    fonts.close();
                }
            }
        } finally {
            configField.set(null, old);
        }
    }

    /**
     * The settings home grid in Yoga terms. LayoutStyle has no margins, no
     * absolute positioning and no aspect ratio, so the page becomes spacer
     * nodes and a measure callback that returns height = width for square
     * tiles. The shape of this tree is itself evidence: it records what a
     * consumer must build to express the page.
     */
    private static LayoutNode gridTree(float topGap, float inset, float gap,
                                       float listW, float listH) {
        LeafMeasure square = c -> {
            float side = c.width().mode() == Constraints.Mode.UNDEFINED ? 0F : c.width().value();
            return new MeasuredSize(side, side, 0F);
        };
        LayoutNode tile0 = LayoutNode.leaf("tile0", tileStyle(), square, 0);
        LayoutNode tile1 = LayoutNode.leaf("tile1", tileStyle(), square, 0);
        LayoutNode tile2 = LayoutNode.leaf("tile2", tileStyle(), square, 0);
        LayoutNode tile3 = LayoutNode.leaf("tile3", tileStyle(), square, 0);
        LayoutNode row0 = new LayoutNode(new NodeId("row0"),
                LayoutStyle.row().spacing(0F, gap), List.of(tile0, tile1), null, 0);
        LayoutNode row1 = new LayoutNode(new NodeId("row1"),
                LayoutStyle.row().spacing(0F, gap), List.of(tile2, tile3), null, 0);
        LayoutNode grid = new LayoutNode(new NodeId("grid"),
                LayoutStyle.column().spacing(0F, gap).flex(1F, 1F, Length.points(0F)),
                List.of(row0, row1), null, 0);
        LayoutNode spacerLeft = LayoutNode.leaf("spacerLeft",
                LayoutStyle.column().size(Length.points(inset), Length.auto()), null, 0);
        LayoutNode spacerRight = LayoutNode.leaf("spacerRight",
                LayoutStyle.column().size(Length.points(inset), Length.auto()), null, 0);
        LayoutNode band = new LayoutNode(new NodeId("band"), LayoutStyle.row(),
                List.of(spacerLeft, grid, spacerRight), null, 0);
        LayoutNode topSpacer = LayoutNode.leaf("topSpacer",
                LayoutStyle.column().size(Length.auto(), Length.points(topGap)), null, 0);
        return new LayoutNode(new NodeId("root"),
                LayoutStyle.column().size(Length.points(listW), Length.points(listH))
                        .behavior(false, false, true, false),
                List.of(topSpacer, band), null, 0);
    }

    private static LayoutStyle tileStyle() {
        return LayoutStyle.row().flex(1F, 1F, Length.points(0F));
    }

    private static void drawTileReplica(Canvas canvas, TextService text, FontId heading,
                                        SettingsSection section, String title, Rect border,
                                        int color, float capHeight) {
        float x = border.x();
        float y = border.y();
        float w = border.width();
        float h = border.height();
        UiCards.drawCard(canvas, x, y, w, h, UiTokens.settingsTileRadius(), 0F);

        // The centred group: glyph above, single label below — the same
        // arithmetic SettingsHomePage.drawTile runs, so only the stack differs.
        Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        float textH = SkiaFontRenderer.textHeight(titleFont);
        float groupH = UiTokens.SETTINGS_TILE_ICON + UiTokens.SETTINGS_TILE_TEXT_GAP + textH;
        float groupTop = y + (h - groupH) / 2.0F;
        float cx = x + w / 2.0F;
        drawIconCentered(canvas, SettingsHomePage.iconFor(section), cx,
                groupTop + UiTokens.SETTINGS_TILE_ICON / 2.0F, UiTokens.SETTINGS_TILE_ICON, color);

        float labelCenterY = groupTop + UiTokens.SETTINGS_TILE_ICON
                + UiTokens.SETTINGS_TILE_TEXT_GAP + textH / 2.0F;
        float maxW = w - UiTokens.s(16);
        var request = new TextRequest(title, heading, UiTokens.SETTINGS_TILE_TITLE, color, 1, true);
        var line = text.layout(request,
                new Constraints(Constraints.Axis.atMost(maxW), Constraints.Axis.undefined()));
        // Host-computed cap-height baseline, matching SkiaFontRenderer.centerBaselineY.
        float baseline = labelCenterY + capHeight / 2.0F;
        text.draw(canvas, line, cx - line.size().width() / 2.0F,
                baseline - line.lines().get(0).baseline());
    }

    /** Same stroke-icon painter SettingsHomePage uses; the path comes from the shared design package. */
    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        var b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /** Icon + label band per tile, with margin — the areas excluded from the pixel threshold. */
    private static List<Rect> textBoxes(UiLayout layout, LayoutSnapshot snapshot) {
        var boxes = new ArrayList<Rect>();
        Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        float textH = SkiaFontRenderer.textHeight(titleFont);
        float groupH = UiTokens.SETTINGS_TILE_ICON + UiTokens.SETTINGS_TILE_TEXT_GAP + textH;
        for (int i = 0; i < 4; i++) {
            var tile = SettingsHomePage.tileRect(layout, i, 0F);
            var border = snapshot.box(new NodeId("tile" + i)).border();
            float x = border.x() + layout.list.x();
            float y = border.y() + layout.list.y();
            float groupTop = y + (tile.h() - groupH) / 2.0F;
            boxes.add(new Rect(Math.max(0, tile.x() - 6), Math.max(0, groupTop - 6),
                    tile.w() + 12, groupH + 12));
        }
        return boxes;
    }

    private static Map<String, Object> pixelSmoke(Surface ac, Surface lib, List<Rect> textBoxes) {
        try (var acBitmap = new Bitmap(); var libBitmap = new Bitmap()) {
            acBitmap.allocN32Pixels(CANVAS_W, CANVAS_H);
            assertTrue(ac.readPixels(acBitmap, 0, 0));
            libBitmap.allocN32Pixels(CANVAS_W, CANVAS_H);
            assertTrue(lib.readPixels(libBitmap, 0, 0));
            byte[] acPixels = acBitmap.readPixels();
            byte[] libPixels = libBitmap.readPixels();
            long total = 0;
            long differing = 0;
            long differingOutsideText = 0;
            long outsideText = 0;
            for (int y = 0; y < CANVAS_H; y++) {
                for (int x = 0; x < CANVAS_W; x++) {
                    int index = (y * CANVAS_W + x) * 4;
                    int delta = 0;
                    for (int c = 0; c < 4; c++) {
                        delta = Math.max(delta, Math.abs(Byte.toUnsignedInt(acPixels[index + c])
                                - Byte.toUnsignedInt(libPixels[index + c])));
                    }
                    boolean inText = false;
                    for (var box : textBoxes) {
                        if (x >= box.x() && x < box.right() && y >= box.y() && y < box.bottom()) {
                            inText = true;
                            break;
                        }
                    }
                    total++;
                    if (!inText) {
                        outsideText++;
                    }
                    if (delta > PIXEL_DELTA_LIMIT) {
                        differing++;
                        if (!inText) {
                            differingOutsideText++;
                        }
                    }
                }
            }
            Map<String, Double> ratios = new LinkedHashMap<>();
            ratios.put("whole", round((double) differing / total));
            ratios.put("excludingText", round((double) differingOutsideText / outsideText));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deltaLimitPerChannel", PIXEL_DELTA_LIMIT);
            result.put("differingPixels", differing);
            result.put("differingOutsideTextBoxes", differingOutsideText);
            result.put("ratios", ratios);
            return result;
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private static void emitResults(List<Check> checks, UiLayout layout,
                                    Map<String, Object> pixels) throws Exception {
        Files.createDirectories(OUTPUT);
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("fixture", "settings-home-replication");
        results.put("acSide", "SettingsHomePage.render on Surface.makeRasterN32Premul, zh_cn, bundled Noto");
        results.put("libSide", "YogaLayoutEngine + TextService + design package, same font bytes");
        results.put("panel", List.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H));
        results.put("list", List.of(layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h()));
        long failed = checks.stream().filter(c -> !c.pass()).count();
        results.put("summary", Map.of("checks", checks.size(), "failed", failed));
        results.put("pixelSmoke", pixels);
        results.put("checks", checks.stream().map(c -> Map.of(
                "name", c.name(), "pass", c.pass(), "detail", c.detail())).toList());
        Path file = OUTPUT.resolve("results.json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(results));
        System.out.println("[settings-home-replication] checks=" + checks.size()
                + " pass=" + (checks.size() - failed) + " failed=" + failed
                + " -> " + file.toAbsolutePath());
        for (Check check : checks) {
            if (!check.pass()) {
                System.out.println("[settings-home-replication] FAILED " + check.name()
                        + ": " + check.detail());
            }
        }
    }

    private static void writePng(Surface surface, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
            Files.write(OUTPUT.resolve(name), data.getBytes());
        }
    }

    private static byte[] resourceBytes(String path) throws Exception {
        try (InputStream in = SettingsHomeReplicationTest.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static int variationCount(byte[] fontBytes) {
        try (Data data = Data.makeFromBytes(fontBytes);
             Typeface face = FontMgr.getDefault().makeFromData(data)) {
            return face == null ? -1 : face.getVariations().length;
        } catch (Exception e) {
            return -1;
        }
    }
}
