package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * The category menu between the settings home and a section page: one
 * horizontal card per category — accent-tinted line icon, title, one-line
 * subtitle and a right chevron. Phone-settings style: the whole card is the
 * tap target, presses bounce with {@link PressScale}, and the geometry comes
 * from {@link #cardRect} so rendering, hit-testing and measurement cannot
 * disagree.
 */
public final class SettingsCategoryPage {
    private static final String SUB_SUFFIX = ".sub";

    private final SettingsSection[] categories = SettingsSection.values();
    private final float[] cardHover = new float[categories.length];
    private final Map<Integer, PressScale> cardPress = new HashMap<>();
    private int pressedCard = -1;
    private long lastFrameMs = System.currentTimeMillis();

    public float measureContent(UiLayout layout) {
        return UiTokens.ROOT_CONTENT_GAP
                + categories.length * UiTokens.SETTINGS_CATEGORY_H
                + (categories.length - 1) * UiTokens.SETTINGS_ROW_GAP;
    }

    /** Geometry of one category card; mirrors the hit test in {@link #cardAt}. */
    public static UiLayout.Rect cardRect(UiLayout layout, int index, float scrollY) {
        return new UiLayout.Rect(
                layout.list.x(),
                layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                        + index * (UiTokens.SETTINGS_CATEGORY_H + UiTokens.SETTINGS_ROW_GAP)
                        - scrollY,
                layout.list.w(), UiTokens.SETTINGS_CATEGORY_H);
    }

    /** Card index under the pointer, or -1. Geometry mirrors {@link #cardRect}. */
    public int cardAt(float vmx, float vmy, UiLayout layout, float scrollY) {
        for (int i = 0; i < categories.length; i++) {
            UiLayout.Rect card = cardRect(layout, i, scrollY);
            if (card.contains(vmx, vmy)) {
                return i;
            }
        }
        return -1;
    }

    /** The category a card opens. */
    public SettingsSection sectionAt(int index) {
        return categories[index];
    }

    /** Arms the card press bounce (mouse-down) or clears it (-1 on release). */
    public void setPressedCard(int index) {
        pressedCard = index;
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        int accent = AtomChatConfig.get().accentColor;
        int textPrimary = AtomChatConfig.get().textPrimaryColor;
        int hovered = -1;

        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(),
                    layout.list.h(), 0.0F);
            for (int i = 0; i < categories.length; i++) {
                UiLayout.Rect card = cardRect(layout, i, scrollY);
                if (card.bottom() < layout.list.y() || card.y() > layout.list.bottom()) {
                    continue;
                }
                boolean over = card.contains(vmx, vmy);
                if (over) {
                    hovered = i;
                }
                PressScale press = cardPress.computeIfAbsent(i, k -> PressScale.row());
                press.update(over, i == pressedCard, dt, Animations.enabled());
                press.begin(canvas, card.x() + card.w() / 2.0F, card.y() + card.h() / 2.0F);
                try {
                    drawCard(canvas, card, categories[i], accent, textPrimary, cardHover[i]);
                } finally {
                    canvas.restore();
                }
            }
        } finally {
            canvas.restore();
        }

        for (int i = 0; i < cardHover.length; i++) {
            cardHover[i] = UiMotion.approach(cardHover[i], i == hovered ? 1.0F : 0.0F, dt,
                    UiMotion.HOVER_MS);
        }
    }

    private void drawCard(Canvas canvas, UiLayout.Rect card, SettingsSection section,
                          int accent, int textPrimary, float hover) {
        UiCards.drawCard(canvas, card.x(), card.y(), card.w(), card.h(),
                UiTokens.settingsRowRadius(), hover);

        float padX = UiTokens.SETTINGS_ROW_PAD;
        float cy = card.y() + card.h() / 2.0F;
        // Icon plate: the section glyph on a faint accent wash, so every card
        // carries a colour identity without images.
        float plate = UiTokens.SETTINGS_CATEGORY_ICON;
        float plateX = card.x() + padX;
        float plateY = cy - plate / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, plateX, plateY, plate, plate, UiTokens.s(10),
                iconPlateTint(accent));
        drawIconCentered(canvas, SettingsHomePage.iconFor(section),
                plateX + plate / 2.0F, cy, UiTokens.s(20), accent);

        Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = plateX + plate + UiTokens.s(12);
        float maxW = card.w() - (textX - card.x()) - padX - chevronRoom();
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, SettingsHomePage.title(section), maxW),
                textX, SkiaFontRenderer.centerBaselineY(titleFont, cy - UiTokens.s(9)),
                textPrimary);
        SkiaFontRenderer.drawText(canvas, subFont,
                SkiaFontRenderer.truncate(subFont,
                        tr(key(section) + SUB_SUFFIX), maxW),
                textX, SkiaFontRenderer.centerBaselineY(subFont, cy + UiTokens.s(11)),
                subColor(200));

        drawChevron(canvas, card.right() - padX - UiTokens.s(5), cy, UiTokens.s(7),
                textPrimary);
    }

    /** Room the right chevron plus its padding reserve on every card. */
    private static float chevronRoom() {
        return UiTokens.s(24);
    }

    private static int iconPlateTint(int accent) {
        return io.github.humbleui.skija.Color.makeARGB(30,
                (accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF);
    }

    private static int subColor(int alpha) {
        int secondary = AtomChatConfig.get().textSecondaryColor;
        return io.github.humbleui.skija.Color.makeARGB(alpha,
                (secondary >> 16) & 0xFF, (secondary >> 8) & 0xFF, secondary & 0xFF);
    }

    /** Small right-pointing chevron: the "this row opens something" affordance. */
    private static void drawChevron(Canvas canvas, float cx, float cy, float size, int color) {
        try (Path path = new Path();
             Paint paint = new Paint()) {
            path.moveTo(cx - size / 2.0F, cy - size);
            path.lineTo(cx + size / 2.0F, cy);
            path.lineTo(cx - size / 2.0F, cy + size);
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
                                         float size, int color) {
        io.github.humbleui.types.Rect b = icon.getBounds();
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

    private static String key(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> "atomchat.settings.appearance";
            case CHAT -> "atomchat.settings.chat";
            case PRIVACY -> "atomchat.settings.privacy";
            case ABOUT -> "atomchat.settings.about";
        };
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** Drops per-page transient state so reopening the menu starts clean. */
    public void reset() {
        pressedCard = -1;
        cardPress.clear();
    }
}
