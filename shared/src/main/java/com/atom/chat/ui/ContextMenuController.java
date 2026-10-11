package com.atom.chat.ui;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

/**
 * The right-click menu (D19): its three modes, its animation state, its drawing
 * and the one geometry both the drawing and every click hit-test measure with.
 *
 * <p><b>Row count and row geometry have exactly one source.</b> The player-card
 * menu once grew a third row while the root page's click test stayed at two, so
 * "取消屏蔽" was permanently unclickable. Everything now goes through
 * {@link #rows(ContextMenuMode, boolean)} and the private bounds built from it:
 * {@link #draw} paints the rows it reports, {@link #hitRow} and
 * {@link #hitPlayerCardRow} answer for exactly those rows. A future row must be
 * added in one place.</p>
 *
 * <p><b>{@link #close()} never recurses.</b> The original called itself, which
 * left the current target set forever and blew the stack on the caller. It hands
 * the open target over to the {@code last*} slots so the closing frame can still
 * draw it (that hand-over is what plays the fade-out), and that is all it does.</p>
 *
 * <p><b>A target is a {@link Subject}, not the mapping layer's message type.</b>
 * The official builds spell that type's chat component {@code Component} and the
 * yarn build {@code Text}, so a message may not be named here (D18b's lesson).
 * {@code M} is the shell's own row identity, which this class only carries: the
 * shell converts once per right-click (sender, image row, identity) and compares
 * the identity itself for the "same row again closes" toggle.</p>
 *
 * <p>Ports on {@link Host}: the shell's labels (D7's lookup), the online test
 * (whose data source is the tab list, D19b's), the panel rect the menu clamps
 * into, this frame's dt and the icon blit. The ports exist so this class — and
 * its test — name no Minecraft API. Nothing here is D19 state except the menu's
 * own fields.</p>
 *
 * <p>One copy, in the shared layer: this class names no Minecraft API, and the
 * drawing types it drives ({@link MenuPopup}, {@link UiTokens}, {@link UiMotion},
 * {@link DrawPrimitives}) already live here. A twin pair would be byte-identical,
 * which {@code verify_targets} rejects.</p>
 */
public final class ContextMenuController<M> {
    /** Which menu is open. */
    public enum ContextMenuMode { BUBBLE, AVATAR, PLAYER_CARD }

    /**
     * What the menu needs about the row it was opened on: the sender it acts on,
     * whether the row carries an image (an extra "save" row) and the shell's own
     * identity handle for that row.
     */
    public interface Subject<M> {
        /** The sender, when the row has one; null otherwise. */
        PlayerRef sender();

        /** True when the row carries an image (adds a save row to the bubble menu). */
        boolean imageMessage();

        /** The shell's row identity, carried opaquely for the toggle comparison. */
        M row();

        /** The shell's adapter: its own facts become this menu's view of a row. */
        static <M> Subject<M> of(PlayerRef sender, boolean imageMessage, M row) {
            return new Subject<M>() {
                @Override
                public PlayerRef sender() {
                    return sender;
                }

                @Override
                public boolean imageMessage() {
                    return imageMessage;
                }

                @Override
                public M row() {
                    return row;
                }
            };
        }
    }

    /** What the screen hands over: labels, the online test, the panel and the sinks. */
    public interface Host {
        /** D7: the language lookup for a context-menu label key. */
        String label(String key);

        /** Whether this player is on the tab list (D19b's source, the dimmed rows). */
        boolean isPlayerOnline(PlayerRef player);

        /** The panel the menu clamps into — the same box the hit tests measure. */
        UiLayout.Rect panelRect();

        /** This frame's dt, the clock every hover/bounce/popup approach runs on. */
        float frameDt();

        /** Blits one menu icon (the shell's primitives, D9). */
        void drawIcon(Canvas canvas, Path icon, float cx, float cy, float size, int color);
    }

    /** The menu's pixel box: one computation, used by the drawing and the hit tests. */
    private record Bounds(float x, float y, float w, float h) {
    }

    private final Host host;

    private Subject<M> subject;
    private PlayerRef player;
    private float x;
    private float y;
    private ContextMenuMode mode = ContextMenuMode.BUBBLE;

    /** Per-cell hover fade for the menu rows. */
    private final float[] hover = new float[4];
    /** Per-row menu bounce, indexed like {@link #hover}. */
    private final PressScale[] scale = {PressScale.bounce(), PressScale.bounce(),
            PressScale.bounce(), PressScale.bounce()};

    private float anim;
    private Subject<M> lastSubject;
    private PlayerRef lastPlayer;
    private ContextMenuMode lastMode = ContextMenuMode.BUBBLE;

    public ContextMenuController(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ opening

    /** Opens the bubble menu on a message row. */
    public void openBubble(Subject<M> target, float menuX, float menuY) {
        mode = ContextMenuMode.BUBBLE;
        subject = target;
        x = menuX;
        y = menuY;
    }

    /** Opens the avatar menu on a message row's sender. */
    public void openAvatar(Subject<M> target, float menuX, float menuY) {
        mode = ContextMenuMode.AVATAR;
        subject = target;
        x = menuX;
        y = menuY;
    }

    /** Opens the player-card menu on a conversation row's player. */
    public void openPlayerCard(PlayerRef target, float menuX, float menuY) {
        mode = ContextMenuMode.PLAYER_CARD;
        player = target;
        x = menuX;
        y = menuY;
        subject = null;
    }

    /** True while a message-backed (bubble or avatar) menu is open. */
    public boolean hasMessageMenu() {
        return subject != null;
    }

    /** True while a player-card menu is open or still playing its close. */
    public boolean hasPlayerCardMenu() {
        return player != null || lastPlayer != null;
    }

    /** The open menu's row identity, or null when no message menu is open. */
    public M currentRow() {
        return subject == null ? null : subject.row();
    }

    /** True when the open menu is the avatar one. */
    public boolean isAvatarMode() {
        return subject != null && mode == ContextMenuMode.AVATAR;
    }

    /** The player a player-card action applies to: the open one, else the closing one. */
    public PlayerRef currentOrLastPlayer() {
        return player != null ? player : lastPlayer;
    }

    /**
     * Hands the open menu over to the {@code last*} slots so it can play the
     * closing animation instead of vanishing. Never recurse: it used to call
     * itself, which left the current target set forever and blew the stack on the
     * caller.
     */
    public void close() {
        if (subject != null) {
            lastSubject = subject;
            lastMode = mode;
            subject = null;
        }
        if (player != null) {
            lastPlayer = player;
            lastMode = mode;
            player = null;
        }
    }

    /** Drops everything, including the closing animation: the world page is leaving. */
    public void resetTransient() {
        close();
        lastSubject = null;
        lastPlayer = null;
        lastMode = ContextMenuMode.BUBBLE;
        anim = 0.0F;
        for (int i = 0; i < hover.length; i++) {
            hover[i] = 0.0F;
        }
    }

    // ----------------------------------------------------------------- geometry

    /**
     * Row count for a context menu. Drawing and every click hit-test go through
     * this one helper so their geometry cannot drift apart: the player-card menu
     * grew a third row (block/unblock) while the root-page click test stayed at
     * two, which made "取消屏蔽" unclickable.
     */
    public static int rows(ContextMenuMode mode, boolean imageMessage) {
        if (mode == ContextMenuMode.AVATAR) {
            return 4;
        }
        if (mode == ContextMenuMode.PLAYER_CARD) {
            return 3;
        }
        return imageMessage ? 3 : 2;
    }

    /** A row's height: the menu token's half, the only place that divides it. */
    private static float rowHeight() {
        return UiTokens.MENU_H / 2.0F;
    }

    /**
     * The menu's box for a mode and an image-row decision — the one geometry the
     * drawing and both hit tests measure with.
     */
    private Bounds bounds(ContextMenuMode forMode, boolean imageMessage) {
        int count = rows(forMode, imageMessage);
        float menuW = UiTokens.MENU_W;
        float menuH = rowHeight() * count;
        UiLayout.Rect panel = host.panelRect();
        float menuX = Math.min(x, panel.x() + panel.w() - menuW - UiTokens.s(8));
        float menuY = Math.min(y, panel.y() + panel.h() - menuH - UiTokens.s(8));
        return new Bounds(menuX, menuY, menuW, menuH);
    }

    /**
     * Whether the image row belongs to this menu: only the bubble menu has one,
     * and only when the row it was opened on carries an image.
     */
    private static <M> boolean imageRow(ContextMenuMode mode, Subject<M> target) {
        return mode == ContextMenuMode.BUBBLE && target != null && target.imageMessage();
    }

    /**
     * The row under the pointer for the open message menu, or -1 when the pointer
     * is outside it (or no message menu is open). Same source as {@link #draw}.
     */
    public int hitRow(float vmx, float vmy) {
        if (subject == null) {
            return -1;
        }
        Bounds b = bounds(mode, imageRow(mode, subject));
        if (vmx < b.x() || vmx > b.x() + b.w() || vmy < b.y() || vmy > b.y() + b.h()) {
            return -1;
        }
        return (int) ((vmy - b.y()) / rowHeight());
    }

    /**
     * The row under the pointer for the player-card menu — the root page's own hit
     * test, whose geometry must come from the same source as {@link #draw}.
     * Player-card menus are always the 3-row layout (profile / tp / block).
     */
    public int hitPlayerCardRow(float vmx, float vmy) {
        if (player == null && lastPlayer == null) {
            return -1;
        }
        Bounds b = bounds(ContextMenuMode.PLAYER_CARD, false);
        if (vmx < b.x() || vmx > b.x() + b.w() || vmy < b.y() || vmy > b.y() + b.h()) {
            return -1;
        }
        return (int) ((vmy - b.y()) / rowHeight());
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas canvas, float vmx, float vmy) {
        boolean hasCurrent = subject != null || player != null;
        boolean hasLast = lastSubject != null || lastPlayer != null;
        if (!hasCurrent && !hasLast) {
            anim = 0.0F;
            return;
        }
        float target = hasCurrent ? 1.0F : 0.0F;
        anim = UiMotion.approach(anim, target, host.frameDt(), Animations.ms(UiMotion.POPUP_MS));
        if (anim < 0.01F) {
            if (!hasCurrent) {
                lastSubject = null;
                lastPlayer = null;
                anim = 0.0F;
            }
            return;
        }
        ContextMenuMode shownMode = hasCurrent ? mode : lastMode;
        boolean avatarMenu = shownMode == ContextMenuMode.AVATAR;
        boolean playerMenu = shownMode == ContextMenuMode.PLAYER_CARD;
        Subject<M> shown = subject != null ? subject : lastSubject;
        boolean imageMessage = imageRow(shownMode, shown);
        Bounds b = bounds(shownMode, imageMessage);
        float menuX = b.x();
        float menuY = b.y();
        float menuW = b.w();
        float menuH = b.h();
        float rowH = rowHeight();
        int count = rows(shownMode, imageMessage);
        for (int row = 0; row < count; row++) {
            float rowY = menuY + row * rowH;
            boolean overRow = vmx >= menuX && vmx <= menuX + menuW
                    && vmy >= rowY && vmy <= rowY + rowH;
            hover[row] = UiMotion.approach(hover[row], overRow ? 1.0F : 0.0F, host.frameDt(), UiMotion.HOVER_MS);
            MenuPopup.updateRowBounce(scale[row], overRow, host.frameDt(), menuW);
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * anim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menuX - UiTokens.s(20), menuY - UiTokens.s(20),
                    menuW + UiTokens.s(40), menuH + UiTokens.s(40)), layer);
            float sc = 0.94F + 0.06F * anim;
            canvas.translate(menuX + menuW / 2.0F, menuY);
            canvas.scale(sc, sc);
            canvas.translate(-(menuX + menuW / 2.0F), -menuY);
            // The one right-click-menu language (MenuPopup owns the plate for
            // every menu in the mod): fixed dark surface (35,39,47), radius
            // and shadow tokens. Fixed polarity by design.
            MenuPopup.drawSurface(canvas, menuX, menuY, menuW, menuH);
            Font menuFont = FontManager.font(UiTokens.FONT_BUTTON);
            for (int row = 0; row < count; row++) {
                float rowY = menuY + row * rowH;
                float hov = hover[row];
                // One bounce per row, the shared menu language: wash, icon and
                // label all travel with the row's own PressScale, centred on
                // the row like the profile avatar menu's items.
                MenuPopup.beginRowBounce(canvas, scale[row], menuX + menuW / 2.0F, rowY + rowH / 2.0F);
                try {
                    MenuPopup.drawRowWash(canvas, menuX, rowY, menuW, rowH, hov);
                    String label;
                    Path icon;
                    if (playerMenu) {
                        label = playerCardContextLabel(row);
                        icon = playerCardContextIcon(row);
                    } else if (avatarMenu) {
                        label = avatarContextLabel(row);
                        icon = avatarContextIcon(row);
                    } else {
                        label = bubbleContextLabel(row, imageMessage);
                        icon = bubbleContextIcon(row, imageMessage);
                    }
                    // Overlay surfaces keep the fixed overlay palette: they sit on
                    // their own opaque dark cards and must not follow the
                    // interface text colour setting.
                    int textColor = Color.makeARGB(255, 255, 255, 255);
                    if (playerMenu && row == 1 && !host.isPlayerOnline(currentOrLastPlayer())) {
                        textColor = Color.makeARGB(110, 255, 255, 255);
                    } else if (avatarMenu && row == 2) {
                        PlayerRef avatarPlayer = shown != null ? shown.sender() : null;
                        if (avatarPlayer != null && !host.isPlayerOnline(avatarPlayer)) {
                            textColor = Color.makeARGB(110, 255, 255, 255);
                        }
                    }
                    host.drawIcon(canvas, icon, menuX + UiTokens.s(18), rowY + rowH / 2.0F,
                            UiTokens.CONTEXT_ICON_SIZE, textColor);
                    SkiaFontRenderer.drawText(canvas, menuFont, label, menuX + UiTokens.s(36),
                            SkiaFontRenderer.centerBaselineY(menuFont, rowY + rowH / 2.0F), textColor);
                } finally {
                    MenuPopup.endRowBounce(canvas);
                }
            }
            canvas.restore();
        }
        canvas.restore();
    }

    // ------------------------------------------------------------------- labels

    private String bubbleContextLabel(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> host.label("atomchat.context.copy");
                case 1 -> host.label("atomchat.context.quote");
                default -> host.label("atomchat.context.save");
            };
        }
        return switch (row) {
            case 0 -> host.label("atomchat.context.copy");
            case 1 -> host.label("atomchat.context.quote");
            case 2 -> host.label("atomchat.context.save");
            default -> "";
        };
    }

    private static Path bubbleContextIcon(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> DrawPrimitives.ICON_COPY_PATH;
                case 1 -> DrawPrimitives.ICON_QUOTE_PATH;
                default -> DrawPrimitives.ICON_SAVE_PATH;
            };
        }
        return switch (row) {
            case 0 -> DrawPrimitives.ICON_COPY_PATH;
            case 1 -> DrawPrimitives.ICON_QUOTE_PATH;
            case 2 -> DrawPrimitives.ICON_SAVE_PATH;
            default -> DrawPrimitives.ICON_SAVE_PATH;
        };
    }

    private String avatarContextLabel(int row) {
        Subject<M> target = subject != null ? subject : lastSubject;
        if (row == 3 && target != null) {
            PlayerRef p = target.sender();
            return BlockList.isBlocked(p) ? host.label("atomchat.context.unblock")
                    : host.label("atomchat.context.block");
        }
        return switch (row) {
            case 0 -> host.label("atomchat.context.mention");
            case 1 -> host.label("atomchat.context.whisper");
            case 2 -> host.label("atomchat.context.tp");
            default -> "";
        };
    }

    private static Path avatarContextIcon(int row) {
        return switch (row) {
            case 0 -> DrawPrimitives.ICON_MENTION_PATH;
            case 1 -> DrawPrimitives.ICON_WHISPER_PATH;
            case 2 -> DrawPrimitives.ICON_TP_PATH;
            case 3 -> DrawPrimitives.ICON_BLOCK_PATH;
            default -> DrawPrimitives.ICON_MENTION_PATH;
        };
    }

    private String playerCardContextLabel(int row) {
        return switch (row) {
            case 0 -> host.label("atomchat.context.profile");
            case 1 -> host.label("atomchat.context.tp");
            case 2 -> isBlockedCurrentPlayer() ? host.label("atomchat.context.unblock")
                    : host.label("atomchat.context.block");
            default -> "";
        };
    }

    private static Path playerCardContextIcon(int row) {
        return switch (row) {
            case 0 -> AppIcons.ICON_TAB_PROFILE_PATH;
            case 1 -> DrawPrimitives.ICON_TP_PATH;
            case 2 -> DrawPrimitives.ICON_BLOCK_PATH;
            default -> DrawPrimitives.ICON_TP_PATH;
        };
    }

    private boolean isBlockedCurrentPlayer() {
        PlayerRef p = currentOrLastPlayer();
        return p != null && BlockList.isBlocked(p);
    }
}
