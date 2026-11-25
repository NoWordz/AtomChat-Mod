package com.atom.chat.channel;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The conversation root's read-only subview. It never borrows the chat composer. */
public final class ChannelFeed {
    private static final float PAD = UiTokens.LIST_PAD_X;
    private static final float LINE = UiTokens.SETTINGS_LABEL_H;
    private static final float GAP = UiTokens.ROOT_CONTENT_GAP;
    private static final float HEADER = 2 * (PAD + LINE);
    private ChannelWire.Key key;
    private long layoutRevision = -1;
    private float width;
    private List<MessageRow> rows = List.of();
    private record MessageRow(String name, List<String> lines) {
        float height() { return 2 * PAD + LINE * (1 + lines.size()); }
    }

    public boolean open(ChannelWire.Key key) {
        if (!ChannelInbox.INSTANCE.open(key, System.nanoTime())) return false;
        closePrevious(key);
        this.key = key;
        layoutRevision = -1;
        return true;
    }

    public void close() {
        closePrevious(null);
        key = null;
        rows = List.of();
    }

    private void closePrevious(ChannelWire.Key next) {
        if (key != null && !key.equals(next)) ChannelInbox.INSTANCE.close(key);
    }

    public ChannelInbox.Row active() {
        if (key == null) return null;
        for (var row : ChannelInbox.INSTANCE.rows(System.nanoTime())) {
            if (row.entry().key().equals(key)) return row;
        }
        close();
        return null;
    }

    public float measure(UiLayout layout) {
        refresh(layout.list.w());
        float total = HEADER + 2 * GAP;
        for (var row : rows) total += row.height() + GAP;
        return total;
    }

    private void refresh(float width) {
        long revision = ChannelInbox.INSTANCE.revision(System.nanoTime());
        if (revision == layoutRevision && this.width == width) return;
        this.width = width;
        layoutRevision = revision;
        var font = FontManager.font(UiTokens.FONT_QUOTE);
        var result = new ArrayList<MessageRow>();
        for (var message : ChannelInbox.INSTANCE.messages(key, System.nanoTime())) {
            var lines = SkiaFontRenderer.wrapWords(font, message.text(), Math.max(1, width - 4 * PAD));
            result.add(new MessageRow(message.name(), lines));
        }
        rows = List.copyOf(result);
    }

    public boolean hitBack(UiLayout layout, float x, float y, float scroll) {
        return layout.list.contains(x, y) && y >= layout.list.y() + GAP - scroll
                && y < layout.list.y() + GAP + HEADER - scroll;
    }

    public void render(Canvas canvas, UiLayout layout, ChannelInbox.Row active, float scroll,
                       float mx, float my, int primary, int secondary, Function<String, String> tr) {
        refresh(layout.list.w());
        var title = FontManager.font(UiTokens.FONT_NAME);
        var text = FontManager.font(UiTokens.FONT_QUOTE);
        float x = layout.list.x() + PAD;
        float w = Math.max(1, layout.list.w() - 2 * PAD);
        float y = layout.list.y() + GAP - scroll;
        int save = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h()));
            card(canvas, x, y, w, HEADER, hitBack(layout, mx, my, scroll) ? 1F : 0F);
            String label = tr.apply("atomchat.channels.back") + " · " + active.entry().label();
            SkiaFontRenderer.drawText(canvas, title, SkiaFontRenderer.truncate(title, label, w - 2 * PAD),
                    x + PAD, y + PAD + LINE / 2, primary);
            SkiaFontRenderer.drawText(canvas, text, tr.apply("atomchat.channels.read_only"),
                    x + PAD, y + HEADER - PAD, secondary);
            y += HEADER + GAP;
            for (var row : rows) {
                float height = row.height();
                if (y > layout.list.bottom()) break;
                if (y + height >= layout.list.y()) {
                    card(canvas, x, y, w, height, 0F);
                    float baseline = y + PAD + LINE * .7F;
                    SkiaFontRenderer.drawText(canvas, title,
                            SkiaFontRenderer.truncate(title, row.name(), w - 2 * PAD), x + PAD, baseline, primary);
                    for (String line : row.lines()) {
                        baseline += LINE;
                        SkiaFontRenderer.drawText(canvas, text, line, x + PAD, baseline, secondary);
                    }
                }
                y += height + GAP;
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static void card(Canvas canvas, float x, float y, float w, float h, float hover) {
        AtomChatAppearance.drawCard(canvas, x, y, w, h, UiTokens.settingsRowRadius(AtomChatAppearance.theme()), hover);
    }
}
