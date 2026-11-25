package com.atom.chat.card;

import static com.atom.chat.api.card.CardLayoutMetrics.*;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardRenderRegistry;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.render.HudShadow;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.List;

import static com.atom.lib.design.atomchat.UiTokens.s;

/**
 * Skija renderer for all built-in {@link ShareCard} templates ({@code LOCATION},
 * {@code RED_PACKET}, {@code ITEM}, {@code GENERAL}) plus {@code CUSTOM} delegation.
 *
 * <p>All cards share one unified width, outer radius, horizontal padding, font scale
 * and footer strip derived from the actual message-lane width so cards sit in harmony
 * with the phone panel and with one another.
 */
public final class BuiltInCardRenderer {

    private static final float MAX_CARD_W = 296F;
    private static final float MIN_CARD_W = 180F;
    /** Unified aspect ratio (height / width) shared across ALL built-in cards (LOCATION, RED_PACKET, ITEM, GENERAL). */
    private static final float STANDARD_RATIO = 0.375F;

    private BuiltInCardRenderer() {
    }

    /** Returns {@code {width, height}} proportional to the actual message-lane {@code maxWidth}. */
    public static float[] measure(ShareCard card, float maxWidth) {
        float w = Math.max(Math.min(maxWidth, s(MIN_CARD_W)), Math.min(maxWidth, s(MAX_CARD_W)));
        if (card == null) return new float[]{w, Math.max(s(EMPTY_CARD_MIN_HEIGHT), w * STANDARD_RATIO)};
        CardState state = CardStateStore.get(card.cardId());
        if (card.template() == ShareCard.Template.CUSTOM && com.atom.chat.config.AtomChatConfig.get().imageMessagesEnabled) {
            CardRenderRegistry.CustomCardRenderer custom = CardRenderRegistry.rendererOf(card.customTypeId());
            if (custom != null) {
                float[] size = custom.measure(card, state, w);
                if (size != null && size.length >= 2 && size[0] > 0 && size[1] > 0) {
                    return new float[]{Math.min(w, size[0]), size[1]};
                }
            }
        }
        float h = clamp(w * STANDARD_RATIO, s(CARD_MIN_HEIGHT), s(CARD_MAX_HEIGHT));
        return new float[]{w, h};
    }

    /** Draws {@code card} inside {@code (x, y, w, h)} and appends interactive hit regions to {@code hitsOut}. */
    public static void draw(Canvas canvas, ShareCard card, float x, float y, float w, float h,
                            boolean own, List<CardHitRegion> hitsOut) {
        if (canvas == null || card == null) return;
        CardState state = CardStateStore.get(card.cardId());
        if (card.template() == ShareCard.Template.CUSTOM && com.atom.chat.config.AtomChatConfig.get().imageMessagesEnabled) {
            CardRenderRegistry.CustomCardRenderer custom = CardRenderRegistry.rendererOf(card.customTypeId());
            if (custom != null) {
                custom.draw(canvas, card, state, x, y, w, h, (rx, ry, rw, rh, action) -> {
                    if (hitsOut != null && action != null) {
                        hitsOut.add(new CardHitRegion(rx, ry, rw, rh, card, state, action, null));
                    }
                });
                return;
            }
        }
        switch (card.template()) {
            case LOCATION -> drawLocation(canvas, card, state, x, y, w, h, hitsOut);
            case RED_PACKET -> drawRedPacket(canvas, card, state, x, y, w, h, hitsOut);
            case ITEM -> drawItem(canvas, card, state, x, y, w, h, hitsOut);
            case GENERAL, CUSTOM -> drawGeneral(canvas, card, state, x, y, w, h, hitsOut);
        }
    }

    // ------------------------------------------------------------------ Unified typography & metrics

    private static float cardRadius() { return UiTokens.settingsRowRadius(com.atom.chat.ui.AtomChatAppearance.theme()); }
    private static float padX() { return s(BODY_PAD); }
    private static float footerHeight(float cardH) { return clamp(cardH * 0.255F, s(FOOTER_MIN_HEIGHT), s(FOOTER_MAX_HEIGHT)); }
    private static Font titleFont() { return FontManager.boldFont(UiTokens.FONT_BODY * 0.84F); }
    private static Font subFont() { return FontManager.font(UiTokens.FONT_QUOTE * 0.86F); }
    private static Font footFont() { return FontManager.font(UiTokens.FONT_QUOTE * 0.78F); }
    private static Font chipFont() { return FontManager.boldFont(UiTokens.FONT_QUOTE * 0.76F); }

    // ------------------------------------------------------------------ 1. LOCATION CARD

    private static void drawLocation(Canvas canvas, ShareCard card, CardState state,
                                     float x, float y, float w, float h, List<CardHitRegion> hitsOut) {
        ShareCard.LocationPayload loc = card.location();
        final String title = loc != null ? loc.name() : card.title();
        final String dim = loc != null ? loc.dimension() : "minecraft:overworld";
        final int accent = loc != null && loc.accentColor() != 0 ? loc.accentColor() : ShareCard.dimensionAccent(dim);
        boolean composer = ComposerLocationCard.isComposerLocation(card);
        final String dimLabel = composer ? ComposerLocationCard.dimensionName(dim, net.minecraft.client.resources.language.I18n::get)
                : BuiltInCardParsers.dimensionDisplayName(dim);
        final String coords = buildLocationCoords(loc);
        String distance = buildLocationDistance(loc, composer);
        String source = composer && !title.equals(card.sourceLabel()) ? card.sourceLabel() : "";
        final String details = source.isEmpty() ? distance : distance.isEmpty() ? source : source + " · " + distance;
        final Image minimapImg = MinimapPreviewSampler.resolveImage(card);

        float radius = cardRadius();
        float pad = padX();
        float footH = footerHeight(h);
        float bodyH = h - footH;
        RRect outline = RRect.makeXYWH(x, y, w, h, radius);
        HudShadow.drawPanel(canvas, outline);

        CornerGeometry.drawClipped(canvas, outline, () -> {
            // Unified crisp white body + light footer strip
            try (Paint bg = new Paint().setColor(0xFFFAFBFC).setAntiAlias(true);
                 Paint footBg = new Paint().setColor(0xFFF4F6FA);
                 Paint div = new Paint().setColor(0xFFE2E8F0)) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, bodyH), bg);
                canvas.drawRect(Rect.makeXYWH(x, y + bodyH, w, footH), footBg);
                canvas.drawRect(Rect.makeXYWH(x, y + bodyH, w, s(HAIRLINE)), div);
            }

            // Left crisp square Minecraft minimap viewport (real terrain sampled from sharer / local chunks)
            float mapPadY = s(MINIMAP_INSET_Y);
            float mh = Math.max(s(MAP_MIN_HEIGHT), bodyH - mapPadY * 2F);
            float mw = Math.min(mh, w * 0.30F);
            float mx = x + s(MEDIA_GAP);
            float my = y + (bodyH - mh) / 2F;
            drawMinimapViewport(canvas, minimapImg, mx, my, mw, mh, dim, accent);

            // Right side: Row 1 Title + Dimension pill, Row 2 Coords, Row 3 Distance + preview cue
            Font tFont = titleFont();
            Font sFont = subFont();
            Font cFont = chipFont();
            Font fFont = footFont();

            float tx = mx + mw + s(MEDIA_GAP);
            float rightEdge = x + w - pad;
            float availW = Math.max(s(LOCATION_TEXT_MIN_WIDTH), rightEdge - tx);

            float tagW = SkiaFontRenderer.getStringWidth(cFont, dimLabel) + s(DIMENSION_LABEL_PAD);
            float tagH = s(DIMENSION_LABEL_HEIGHT);
            float tagX = rightEdge - tagW;
            boolean hasDetails = !details.isEmpty();
            float row1Cy = y + bodyH * (hasDetails ? 0.25F : 0.34F);
            CornerGeometry.drawRoundedRect(canvas, tagX, row1Cy - tagH / 2F, tagW, tagH, s(DIMENSION_LABEL_RADIUS), withAlpha(accent, 0x22));
            SkiaFontRenderer.drawTextCentered(canvas, cFont, dimLabel,
                    tagX + tagW / 2F, row1Cy, darken(accent, 0.66F));

            String truncTitle = SkiaFontRenderer.truncate(tFont, title, Math.max(s(ILLUSTRATED_TEXT_MIN_WIDTH), tagX - tx - s(SMALL_CORNER)));
            SkiaFontRenderer.drawText(canvas, tFont, truncTitle,
                    tx, SkiaFontRenderer.centerBaselineY(tFont, row1Cy), 0xFF0F172A, false);

            float row2Cy = y + bodyH * (hasDetails ? 0.54F : 0.69F);
            String truncCoords = SkiaFontRenderer.truncate(sFont, coords, availW);
            SkiaFontRenderer.drawText(canvas, sFont, truncCoords,
                    tx, SkiaFontRenderer.centerBaselineY(sFont, row2Cy), 0xFF475569, false);

            if (hasDetails) {
                float row3Cy = y + bodyH * 0.80F;
                String truncatedDetails = SkiaFontRenderer.truncate(fFont, details, availW);
                SkiaFontRenderer.drawText(canvas, fFont, truncatedDetails,
                        tx, SkiaFontRenderer.centerBaselineY(fFont, row3Cy), 0xFF64748B, false);
            }

            // Unified footer: clean action pills only (no redundant "位置分享" filler label)
            float footY = y + bodyH;
            float footCy = footY + footH * 0.48F;
            float btnX = x + pad;
            float btnH = s(COMPACT_ACTION_HEIGHT);
            float btnY = footCy - btnH / 2F;
            float gap = s(COMPACT_GAP);
            var actions = card.actions().stream().filter(action -> !action.label().isBlank()).toList();
            float available = rightEdge - btnX - gap * (actions.size() - 1);
            float[] widths = new float[actions.size()];
            float desiredTotal = 0;
            for (int i = 0; i < actions.size(); i++) {
                widths[i] = SkiaFontRenderer.getStringWidth(cFont, actions.get(i).label()) + s(MEDIA_TEXT_GAP);
                desiredTotal += widths[i];
            }
            float scale = Math.min(1, available / Math.max(1, desiredTotal));
            for (int i = 0; i < actions.size() && scale > 0; i++) {
                var action = actions.get(i);
                float bw = composer ? widths[i] * scale : Math.min(widths[i], available / actions.size());
                boolean enabled = CardActionExecutor.actionEnabled(action, state);
                int pillBg = enabled && action.primary() ? accent : withAlpha(accent, 0x22);
                int pillText = enabled && action.primary() ? 0xFFFFFFFF : darken(accent, 0.60F);
                CornerGeometry.drawRoundedRect(canvas, btnX, btnY, bw, btnH, s(SMALL_CORNER), pillBg);
                String label = SkiaFontRenderer.truncate(cFont, action.label(), Math.max(1, bw - s(INNER_INSET)));
                SkiaFontRenderer.drawTextCentered(canvas, cFont, label, btnX + bw / 2F, footCy, pillText);
                if (enabled && hitsOut != null) hitsOut.add(new CardHitRegion(btnX, btnY, bw, btnH, card, state, action, null));
                btnX += bw + gap;
            }
        });

        drawBorder(canvas, outline, 0x240F172A);

        ShareCard.CardAction bodyAct = effectiveBodyAction(card, state);
        if (hitsOut != null && bodyAct != null) {
            hitsOut.add(new CardHitRegion(x, y, w, h, card, state, bodyAct, null));
        }
    }

    private static void drawMinimapViewport(Canvas canvas, Image minimapImg,
                                            float x, float y, float w, float h,
                                            String dim, int accent) {
        RRect frame = RRect.makeXYWH(x, y, w, h, s(MAP_RADIUS));
        CornerGeometry.drawClipped(canvas, frame, () -> {
            if (minimapImg != null) {
                Rect src = Rect.makeWH(minimapImg.getWidth(), minimapImg.getHeight());
                Rect dst = Rect.makeXYWH(x, y, w, h);
                canvas.drawImageRect(minimapImg, src, dst, SamplingMode.DEFAULT, null, false);
                // Very subtle chunk crosshair grid so it reads unmistakably as a Minecraft minimap
                try (Paint grid = new Paint().setColor(0x1CFFFFFF).setStrokeWidth(s(MAP_GRID_STROKE))) {
                    canvas.drawLine(x + w * 0.5F, y, x + w * 0.5F, y + h, grid);
                    canvas.drawLine(x, y + h * 0.5F, x + w, y + h * 0.5F, grid);
                }
            } else {
                drawPixelFallbackMinimap(canvas, x, y, w, h, dim);
            }

            // Tiny top-center 'N' compass badge (classic Xaero / MC minimap cue)
            Font nFont = FontManager.boldFont(UiTokens.FONT_QUOTE * 0.62F);
            float nw = s(COMPASS_BADGE_WIDTH);
            float nh = s(COMPASS_BADGE_HEIGHT);
            float nx = x + (w - nw) / 2F;
            float ny = y + s(INNER_INSET);
            CornerGeometry.drawRoundedRect(canvas, nx, ny, nw, nh, s(COMPACT_GAP), 0xA80F172A);
            SkiaFontRenderer.drawTextCentered(canvas, nFont, "N", nx + nw / 2F, ny + nh / 2F, 0xFFF8FAFC);

            // Centered crisp waypoint marker (white halo + dimension accent core)
            float cx = x + w * 0.5F;
            float cy = y + h * 0.52F;
            try (Paint shadow = new Paint().setColor(0x58000000).setAntiAlias(true);
                 Paint ring = new Paint().setColor(0xFFFFFFFF).setAntiAlias(true);
                 Paint core = new Paint().setColor(accent).setAntiAlias(true)) {
                canvas.drawCircle(cx, cy + s(HAIRLINE), s(MAP_MARKER_SHADOW), shadow);
                canvas.drawCircle(cx, cy, s(MAP_MARKER_RING), ring);
                canvas.drawCircle(cx, cy, s(MAP_MARKER_CORE), core);
            }
        });
        // Crisp dark minimap bezel border
        try (Paint border = new Paint().setColor(0x520F172A).setMode(PaintMode.STROKE)
                .setStrokeWidth(s(MAP_FRAME_STROKE)).setAntiAlias(true)) {
            canvas.drawRRect(frame, border);
        }
    }

    /** Compact pixel-terrain fallback used only when target chunks are unloaded on both sides. */
    private static void drawPixelFallbackMinimap(Canvas canvas, float x, float y, float w, float h, String dim) {
        String lower = dim == null ? "" : dim.toLowerCase(java.util.Locale.ROOT);
        int[] palette;
        if (lower.contains("nether")) {
            palette = new int[]{0xFF5C1F24, 0xFF4B181D, 0xFF73262C, 0xFFD9641E, 0xFF3E1418};
        } else if (lower.contains("end")) {
            palette = new int[]{0xFF3B245C, 0xFF2D1B4E, 0xFFCFC788, 0xFF4F327A, 0xFF21133A};
        } else {
            palette = new int[]{0xFF6BA64E, 0xFF5D9643, 0xFF7BB45B, 0xFF3B82C4, 0xFF8C7B63};
        }
        int cols = 12;
        int rows = 9;
        float cw = w / cols;
        float ch = h / rows;
        try (Paint p = new Paint()) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx;
                    if (Math.abs(c - (rows - r) * 1.1F) < 1.1F) {
                        idx = 3; // diagonal river / lava / end-island strip
                    } else if (c == 4 || r == 5) {
                        idx = 4; // subtle road / contour
                    } else {
                        idx = (r * 3 + c * 5) % 3;
                    }
                    p.setColor(palette[idx]);
                    canvas.drawRect(Rect.makeXYWH(x + c * cw, y + r * ch, cw + 0.5F, ch + 0.5F), p);
                }
            }
        }
        try (Paint grid = new Paint().setColor(0x22FFFFFF).setStrokeWidth(s(MAP_GRID_STROKE))) {
            canvas.drawLine(x + w * 0.5F, y, x + w * 0.5F, y + h, grid);
            canvas.drawLine(x, y + h * 0.5F, x + w, y + h * 0.5F, grid);
        }
    }

    private static String buildLocationCoords(ShareCard.LocationPayload loc) {
        if (loc == null) return "";
        return "X:" + loc.blockX() + "  Y:" + loc.blockY() + "  Z:" + loc.blockZ();
    }

    private static String buildLocationDistance(ShareCard.LocationPayload loc, boolean composer) {
        if (loc == null) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            String playerDim = mc.level.dimension().location().toString();
            if (playerDim.equalsIgnoreCase(loc.dimension())) {
                double dx = loc.x() - mc.player.getX();
                double dy = loc.y() - mc.player.getY();
                double dz = loc.z() - mc.player.getZ();
                int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                return composer ? net.minecraft.client.resources.language.I18n.get(ComposerLocationCard.PREFIX + "distance", dist)
                        : "距你 " + dist + "m";
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ 2. RED PACKET CARD

    private static void drawRedPacket(Canvas canvas, ShareCard card, CardState state,
                                      float x, float y, float w, float h, List<CardHitRegion> hitsOut) {
        ShareCard.RedPacketPayload rp = card.redPacket();
        final boolean dimmed = (state != null && state.dimmed()) || (rp != null && rp.dimmed());
        final String greeting = rp != null ? rp.greeting() : card.title();
        String rawStatus = (state != null && state.statusText() != null && !state.statusText().isBlank())
                ? state.statusText()
                : (rp != null && !rp.statusText().isBlank() ? rp.statusText() : card.subtitle());
        final String status = (rawStatus == null || rawStatus.isBlank())
                ? (dimmed ? "已领取" : "") : rawStatus;
        final String rawSource = card.sourceLabel().isBlank() ? "模块商店" : card.sourceLabel();
        final String source = resolveRedPacketSourceLabel(rawSource);
        final String badge = (state != null && state.badgeText() != null)
                ? state.badgeText() : card.badgeText();

        float radius = cardRadius();
        float pad = padX();
        float footH = footerHeight(h);
        float bodyH = h - footH;
        RRect outline = RRect.makeXYWH(x, y, w, h, radius);
        HudShadow.drawPanel(canvas, outline);

        int c0 = dimmed ? 0xFFF2C699 : 0xFFFA9D3B;
        int c1 = dimmed ? 0xFFE6B484 : 0xFFEE7A24;

        CornerGeometry.drawClipped(canvas, outline, () -> {
            try (Shader grad = Shader.makeLinearGradient(x, y, x + w, y + h, new int[]{c0, c1});
                 Paint fill = new Paint().setShader(grad).setAntiAlias(true)) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), fill);
            }
            // Unified translucent footer strip
            try (Paint foot = new Paint().setColor(dimmed ? 0x38FFFFFF : 0x26FFFFFF)) {
                canvas.drawRect(Rect.makeXYWH(x, y + bodyH, w, footH), foot);
            }

            // Compact vector red-envelope icon (proportionally scaled to body)
            float iconH = clamp(bodyH * 0.64F, s(ICON_MIN_SIZE), s(ICON_MAX_SIZE));
            float iconW = iconH * 0.86F;
            float iconX = x + pad;
            float iconY = y + (bodyH - iconH) / 2F;
            RRect envRect = RRect.makeXYWH(iconX, iconY, iconW, iconH, s(ACTION_CORNER));
            CornerGeometry.drawClipped(canvas, envRect, () -> {
                try (Paint envBg = new Paint().setColor(dimmed ? 0xFFDE7C73 : 0xFFE53935).setAntiAlias(true)) {
                    canvas.drawRect(Rect.makeXYWH(iconX, iconY, iconW, iconH), envBg);
                }
                try (Paint flap = new Paint().setColor(dimmed ? 0xE6FEF3C7 : 0xFFFEF3C7).setAntiAlias(true)) {
                    canvas.drawOval(Rect.makeXYWH(iconX - s(COMPACT_GAP), iconY - iconH * 0.36F, iconW + s(WIDGET_GAP), iconH * 0.64F), flap);
                }
                float coinCx = iconX + iconW / 2F;
                float coinCy = iconY + iconH * 0.42F;
                try (Paint rim = new Paint().setColor(0xFFFEF08A).setAntiAlias(true);
                     Paint coin = new Paint().setColor(dimmed ? 0xFFEAC475 : 0xFFFBBF24).setAntiAlias(true)) {
                    canvas.drawCircle(coinCx, coinCy, s(COIN_RIM_RADIUS), rim);
                    canvas.drawCircle(coinCx, coinCy, s(COIN_CORE_RADIUS), coin);
                }
                Font coinFont = FontManager.boldFont(s(COIN_FONT));
                String sym = (rp != null && rp.hasPassword()) ? "口" : "¥";
                SkiaFontRenderer.drawTextCentered(canvas, coinFont, sym, coinCx, coinCy, 0xFFB45309);
            });

            // Right greeting + live status
            float textX = iconX + iconW + s(MEDIA_TEXT_GAP);
            float textMaxW = Math.max(s(ILLUSTRATED_TEXT_MIN_WIDTH), x + w - textX - pad);
            Font tFont = titleFont();
            Font sFont = subFont();
            int titleCol = dimmed ? 0xFF4C2E20 : 0xFF331708;
            int subCol = 0xFF49250F;
            SkiaFontRenderer.drawText(canvas, tFont,
                    SkiaFontRenderer.truncate(tFont, greeting, textMaxW),
                    textX, SkiaFontRenderer.centerBaselineY(tFont, y + bodyH * 0.36F), titleCol, false);
            SkiaFontRenderer.drawText(canvas, sFont,
                    SkiaFontRenderer.truncate(sFont, status, textMaxW),
                    textX, SkiaFontRenderer.centerBaselineY(sFont, y + bodyH * 0.69F), subCol, false);

            // Unified footer
            Font fFont = footFont();
            float footCy = y + bodyH + footH / 2F;
            int footCol = 0xFF49250F;
            SkiaFontRenderer.drawText(canvas, fFont, source,
                    x + pad, SkiaFontRenderer.centerBaselineY(fFont, footCy), footCol, false);
            if (badge != null && !badge.isBlank()) {
                float bw = SkiaFontRenderer.getStringWidth(fFont, badge);
                SkiaFontRenderer.drawText(canvas, fFont, badge,
                        x + w - pad - bw, SkiaFontRenderer.centerBaselineY(fFont, footCy), footCol, false);
            }
        });

        ShareCard.CardAction bodyAct = effectiveBodyAction(card, state);
        if (hitsOut != null && bodyAct != null) {
            hitsOut.add(new CardHitRegion(x, y, w, h, card, state, bodyAct, null));
        }
    }

    // ------------------------------------------------------------------ 3. ITEM / EQUIPMENT CARD

    private static void drawItem(Canvas canvas, ShareCard card, CardState state,
                                 float x, float y, float w, float h, List<CardHitRegion> hitsOut) {
        ShareCard.ItemPayload item = card.item();
        final String name = item != null ? item.displayName() : card.title();
        final String summary = item != null && !item.summary().isBlank() ? item.summary() : card.subtitle();
        final String rarity = item != null && !item.rarityLabel().isBlank() ? item.rarityLabel() : card.badgeText();
        final int accent = item != null && item.accentColor() != 0 ? item.accentColor() : card.accentColor();
        final int count = item != null ? item.count() : 1;
        final String footerLabel = (rarity != null && !rarity.isBlank()) ? rarity : "";

        HoverEvent hover = BuiltInCardAdapter.resolveItemHover(card);
        HoverEvent.ItemStackInfo itemInfo = hover != null ? hover.getValue(HoverEvent.Action.SHOW_ITEM) : null;

        float radius = cardRadius();
        float pad = padX();
        float footH = footerHeight(h);
        float bodyH = h - footH;
        RRect outline = RRect.makeXYWH(x, y, w, h, radius);
        HudShadow.drawPanel(canvas, outline);

        CornerGeometry.drawClipped(canvas, outline, () -> {
            try (Paint bg = new Paint().setColor(0xFFFCFDFF).setAntiAlias(true)) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), bg);
            }
            // Left rarity accent stripe
            try (Paint bar = new Paint().setColor(accent)) {
                canvas.drawRect(Rect.makeXYWH(x, y, s(ACCENT_STRIPE), h), bar);
            }
            // Unified footer background + divider
            try (Paint footBg = new Paint().setColor(0xFFF4F6FA);
                 Paint div = new Paint().setColor(0xFFE2E8F0)) {
                canvas.drawRect(Rect.makeXYWH(x + s(ACCENT_STRIPE), y + bodyH, w - s(ACCENT_STRIPE), footH), footBg);
                canvas.drawRect(Rect.makeXYWH(x + s(ACCENT_STRIPE), y + bodyH, w - s(ACCENT_STRIPE), s(HAIRLINE)), div);
            }

            // Compact 34x34 item slot (gives maximum horizontal space to full item name!)
            float slotSize = clamp(bodyH * 0.64F, s(ICON_MIN_SIZE), s(ICON_MAX_SIZE));
            float slotX = x + pad + s(INNER_INSET);
            float slotY = y + (bodyH - slotSize) / 2F;
            RRect slotRect = RRect.makeXYWH(slotX, slotY, slotSize, slotSize, s(ITEM_SLOT_RADIUS));
            try (Shader slotGrad = Shader.makeLinearGradient(slotX, slotY, slotX + slotSize, slotY + slotSize,
                    new int[]{darken(accent, 0.28F), darken(accent, 0.46F)});
                 Paint slotFill = new Paint().setShader(slotGrad).setAntiAlias(true)) {
                CornerGeometry.drawRRect(canvas, slotRect, slotFill);
            }
            drawBorder(canvas, slotRect, withAlpha(accent, 0xB0));

            if (itemInfo != null) {
                float iconPad = s(SMALL_CORNER);
                ItemIconRenderer.draw(canvas, itemInfo, slotX + iconPad, slotY + iconPad, slotSize - iconPad * 2F);
            }
            if (count > 1) {
                Font cntFont = FontManager.boldFont(s(ITEM_COUNT_FONT));
                String cnt = "×" + count;
                float cw = SkiaFontRenderer.getStringWidth(cntFont, cnt);
                SkiaFontRenderer.drawText(canvas, cntFont, cnt,
                        slotX + slotSize - cw - s(ITEM_COUNT_INSET), slotY + slotSize - s(ITEM_COUNT_INSET), 0xFFFFFFFF, true);
            }

            // Full-width item name (rarity moved cleanly to footer so title doesn't get pinched!)
            float textX = slotX + slotSize + s(MEDIA_TEXT_GAP);
            float textMaxW = Math.max(s(ILLUSTRATED_TEXT_MIN_WIDTH), x + w - textX - pad);
            Font tFont = titleFont();
            Font sFont = subFont();

            SkiaFontRenderer.drawText(canvas, tFont,
                    SkiaFontRenderer.truncate(tFont, name, textMaxW),
                    textX, SkiaFontRenderer.centerBaselineY(tFont, y + bodyH * 0.36F), darken(accent, 0.72F), false);

            if (summary != null && !summary.isBlank()) {
                SkiaFontRenderer.drawText(canvas, sFont,
                        SkiaFontRenderer.truncate(sFont, summary, textMaxW),
                        textX, SkiaFontRenderer.centerBaselineY(sFont, y + bodyH * 0.69F), 0xFF475569, false);
            }

            // Unified footer: rarity on left + optional single action pill (e.g. Auction Buy) on right
            Font fFont = footFont();
            float footCy = y + bodyH + footH * 0.48F;
            if (!footerLabel.isBlank()) {
                SkiaFontRenderer.drawText(canvas, fFont, footerLabel,
                        x + pad + s(INNER_INSET), SkiaFontRenderer.centerBaselineY(fFont, footCy), darken(accent, 0.72F), false);
            }
            if (!card.actions().isEmpty()) {
                ShareCard.CardAction act = card.actions().get(0);
                if (act != null && !act.label().isBlank()) {
                    Font cFont = chipFont();
                    float bw = SkiaFontRenderer.getStringWidth(cFont, act.label()) + s(ITEM_ACTION_PAD);
                    float bh = s(COMPACT_ACTION_HEIGHT);
                    float bx = x + w - pad - bw;
                    float by = footCy - bh / 2F;
                    CornerGeometry.drawRoundedRect(canvas, bx, by, bw, bh, s(SMALL_CORNER), accent);
                    SkiaFontRenderer.drawTextCentered(canvas, cFont, act.label(),
                            bx + bw / 2F, footCy, 0xFFFFFFFF);
                    if (hitsOut != null) {
                        hitsOut.add(new CardHitRegion(bx, by, bw, bh, card, state, act, null));
                    }
                }
            }
        });

        drawBorder(canvas, outline, 0x240F172A);

        Style hoverStyle = hover != null ? Style.EMPTY.withHoverEvent(hover) : null;
        ShareCard.CardAction bodyAct = effectiveBodyAction(card, state);
        if (hitsOut != null && (hoverStyle != null || bodyAct != null)) {
            hitsOut.add(new CardHitRegion(x, y, w, h, card, state, bodyAct, hoverStyle));
        }
    }

    // ------------------------------------------------------------------ 4. GENERAL / TPA / MINI-PROGRAM CARD

    private static void drawGeneral(Canvas canvas, ShareCard card, CardState state,
                                    float x, float y, float w, float h, List<CardHitRegion> hitsOut) {
        ShareCard.GeneralPayload gen = card.general();
        final boolean dimmed = state != null && state.dimmed();
        final String title = gen != null && !gen.title().isBlank() ? gen.title() : card.title();
        final String desc = (state != null && state.statusText() != null && !state.statusText().isBlank())
                ? state.statusText()
                : (gen != null && !gen.description().isBlank() ? gen.description() : card.subtitle());
        String rawSource = card.sourceLabel() == null ? "" : card.sourceLabel().trim();
        if (rawSource.endsWith(" · 传送请求")) {
            rawSource = rawSource.substring(0, rawSource.length() - " · 传送请求".length());
        }
        final String source = "分享卡片".equals(rawSource) ? "" : rawSource;
        final String badge = (state != null && state.badgeText() != null) ? state.badgeText() : card.badgeText();
        int rawAccent = gen != null && gen.accentColor() != 0 ? gen.accentColor() : card.accentColor();
        final int accent = dimmed ? 0xFF94A3B8 : rawAccent;

        float radius = cardRadius();
        float pad = padX();
        float footH = footerHeight(h);
        boolean showButtons = hasVisibleButtons(card, state);
        float bodyH = h - footH;
        RRect outline = RRect.makeXYWH(x, y, w, h, radius);
        HudShadow.drawPanel(canvas, outline);

        int bgColor = dimmed ? 0xFFF1F5F9 : 0xFFFCFDFF;
        int titleColor = dimmed ? 0xFF64748B : 0xFF0F172A;
        int descColor = dimmed ? 0xFF94A3B8 : 0xFF64748B;

        CornerGeometry.drawClipped(canvas, outline, () -> {
            try (Paint bg = new Paint().setColor(bgColor).setAntiAlias(true)) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), bg);
            }
            // Left accent stripe (matches ITEM card visual language!)
            try (Paint leftBar = new Paint().setColor(accent)) {
                canvas.drawRect(Rect.makeXYWH(x, y, s(ACCENT_STRIPE), h), leftBar);
            }
            // Unified footer background + divider
            try (Paint footBg = new Paint().setColor(dimmed ? 0xFFE2E8F0 : 0xFFF4F6FA);
                 Paint div = new Paint().setColor(0xFFE2E8F0)) {
                canvas.drawRect(Rect.makeXYWH(x + s(ACCENT_STRIPE), y + bodyH, w - s(ACCENT_STRIPE), footH), footBg);
                canvas.drawRect(Rect.makeXYWH(x + s(ACCENT_STRIPE), y + bodyH, w - s(ACCENT_STRIPE), s(HAIRLINE)), div);
            }

            float contentX = x + pad + s(INNER_INSET);
            // Only show a compact 32px thumbnail when there's a real URL image (skip decorative letter boxes that pinch text)
            boolean hasImageThumb = gen != null && !gen.thumbnailUrl().isBlank();
            boolean allowThumbnail = com.atom.chat.config.AtomChatConfig.get().imageMessagesEnabled;
            float thumbSize = s(THUMBNAIL_SIZE);
            float textMaxW = w - (pad * 2F + s(INNER_INSET)) - (hasImageThumb ? thumbSize + s(THUMBNAIL_GAP) : 0F);

            Font tFont = titleFont();
            Font sFont = subFont();

            if (showButtons) {
                // Exact same 2-row rhythm & height as RedPacket / Item cards:
                // Row 1 = Title, Row 2 = Compact interactive action buttons
                SkiaFontRenderer.drawText(canvas, tFont,
                        SkiaFontRenderer.truncate(tFont, title, textMaxW),
                        contentX, SkiaFontRenderer.centerBaselineY(tFont, y + bodyH * 0.31F), titleColor, false);

                boolean disabled = state != null && state.disableActions();
                float btnH = s(BODY_ACTION_HEIGHT);
                float btnY = y + bodyH * 0.71F - btnH / 2F;
                float bx = contentX;
                Font bFont = chipFont();
                int drawn = 0;
                for (ShareCard.CardAction act : card.actions()) {
                    if (drawn >= 2 || act.label().isBlank()) continue;
                    float bw = Math.max(s(BODY_ACTION_MIN_WIDTH), SkiaFontRenderer.getStringWidth(bFont, act.label()) + s(ACTION_LABEL_PAD));
                    if (bx + bw > x + w - pad) break;
                    int fillCol = disabled
                            ? 0xFFE2E8F0
                            : (act.primary() ? accent : withAlpha(accent, 0x1E));
                    int textCol = disabled
                            ? 0xFF94A3B8
                            : (act.primary() ? 0xFFFFFFFF : darken(accent, 0.78F));
                    CornerGeometry.drawRoundedRect(canvas, bx, btnY, bw, btnH, s(ACTION_CORNER), fillCol);
                    SkiaFontRenderer.drawTextCentered(canvas, bFont, act.label(),
                            bx + bw / 2F, btnY + btnH / 2F, textCol);
                    if (!disabled && hitsOut != null) {
                        hitsOut.add(new CardHitRegion(bx, btnY, bw, btnH, card, state, act, null));
                    }
                    bx += bw + s(WIDGET_GAP);
                    drawn++;
                }
            } else {
                SkiaFontRenderer.drawText(canvas, tFont,
                        SkiaFontRenderer.truncate(tFont, title, textMaxW),
                        contentX, SkiaFontRenderer.centerBaselineY(tFont, y + bodyH * 0.36F), titleColor, false);
                if (desc != null && !desc.isBlank()) {
                    SkiaFontRenderer.drawText(canvas, sFont,
                            SkiaFontRenderer.truncate(sFont, desc, textMaxW),
                            contentX, SkiaFontRenderer.centerBaselineY(sFont, y + bodyH * 0.69F), descColor, false);
                }
            }

            if (hasImageThumb) {
                float tx = x + w - pad - thumbSize;
                float ty = y + (bodyH - thumbSize) / 2F;
                RRect thumbR = RRect.makeXYWH(tx, ty, thumbSize, thumbSize, s(THUMBNAIL_RADIUS));
                io.github.humbleui.skija.Image img = allowThumbnail ? ImageLoader.get().get(gen.thumbnailUrl(), true) : null;
                if (img != null) {
                    CornerGeometry.drawClipped(canvas, thumbR, () ->
                            canvas.drawImageRect(img, Rect.makeXYWH(tx, ty, thumbSize, thumbSize)));
                } else {
                    CornerGeometry.drawRoundedRect(canvas, tx, ty, thumbSize, thumbSize, s(THUMBNAIL_RADIUS), withAlpha(accent, 0x22));
                }
            }

            // Unified footer
            Font fFont = footFont();
            float footCy = y + bodyH + footH / 2F;
            if (!source.isBlank()) {
                SkiaFontRenderer.drawText(canvas, fFont, source,
                        contentX, SkiaFontRenderer.centerBaselineY(fFont, footCy), 0xFF64748B, false);
            }
            if (badge != null && !badge.isBlank()) {
                float bw = SkiaFontRenderer.getStringWidth(fFont, badge);
                SkiaFontRenderer.drawText(canvas, fFont, badge,
                        x + w - pad - bw, SkiaFontRenderer.centerBaselineY(fFont, footCy),
                        dimmed ? 0xFF94A3B8 : darken(accent, 0.78F), false);
            }
        });

        drawBorder(canvas, outline, 0x240F172A);

        ShareCard.CardAction bodyAct = effectiveBodyAction(card, state);
        if (hitsOut != null && bodyAct != null) {
            hitsOut.add(new CardHitRegion(x, y, w, h, card, state, bodyAct, null));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static volatile boolean shopTitleFieldLookedUp;
    private static java.lang.reflect.Field shopTitleField;

    /** Maps Module-Shop red-packet source label to the clean brand name without redundant "· 红包". */
    private static String resolveRedPacketSourceLabel(String rawSource) {
        if (rawSource == null || rawSource.isBlank()) {
            String custom = readModuleShopClientTitle();
            return custom == null || custom.isBlank() ? "模块商店" : custom;
        }
        String base = rawSource.endsWith(" · 红包")
                ? rawSource.substring(0, rawSource.length() - " · 红包".length()).trim()
                : rawSource.trim();
        if ("模块商店".equals(base)) {
            String custom = readModuleShopClientTitle();
            return custom == null || custom.isBlank() ? "模块商店" : custom;
        }
        return base;
    }

    private static String readModuleShopClientTitle() {
        try {
            if (!shopTitleFieldLookedUp) {
                shopTitleFieldLookedUp = true;
                Class<?> cls = Class.forName("cn.autoforged.module_shop.client.ClientShopData");
                shopTitleField = cls.getField("shopTitle");
            }
            if (shopTitleField != null) {
                Object val = shopTitleField.get(null);
                if (val instanceof String s && !s.isBlank()) {
                    return BuiltInCardParsers.sanitizeLabel(s, 20);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean hasVisibleButtons(ShareCard card, CardState state) {
        if (card == null || card.actions().isEmpty()) return false;
        for (ShareCard.CardAction a : card.actions()) {
            if (a != null && !a.label().isBlank()) return true;
        }
        return false;
    }

    private static ShareCard.CardAction effectiveBodyAction(ShareCard card, CardState state) {
        if (state != null && state.overrideBodyAction() != null) {
            return state.overrideBodyAction();
        }
        return card.bodyAction();
    }

    private static void drawBorder(Canvas canvas, RRect rect, int argb) {
        try (Paint border = new Paint().setColor(argb).setMode(PaintMode.STROKE)
                .setStrokeWidth(s(HAIRLINE)).setAntiAlias(true)) {
            CornerGeometry.drawRRect(canvas, rect, border);
        }
    }

    private static int withAlpha(int argb, int alphaByte) {
        return ((alphaByte & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, Math.round(((argb >>> 16) & 0xFF) * factor)));
        int g = Math.min(255, Math.max(0, Math.round(((argb >>> 8) & 0xFF) * factor)));
        int b = Math.min(255, Math.max(0, Math.round((argb & 0xFF) * factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
