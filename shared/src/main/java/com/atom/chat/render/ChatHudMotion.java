package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiSpring;
import com.atom.lib.design.atomchat.UiTokens;

import java.util.ArrayList;
import java.util.List;

/** The newest targets and a bounded set of departing rows share the HUD's frame clock. */
final class ChatHudMotion<T> {
    private static final int POOL_SIZE = AtomChatConfig.CHAT_HUD_MAX_MESSAGES * 2;
    private final List<Row<T>> rows = new ArrayList<>(POOL_SIZE);
    private long now;
    private boolean animate;
    private boolean arriving;

    ChatHudMotion() {
        for (int i = 0; i < POOL_SIZE; i++) rows.add(new Row<>());
    }

    static final class Row<T> {
        private Object key;
        private T content;
        private boolean retained;
        private boolean departing;
        private long entryStart;
        private long moveStart;
        private long exitStart;
        private float entryX;
        private float fromY;
        private float toY;
        private float exitX;
        private float exitAlpha;

        T content() { return content; }
        boolean departing() { return departing; }

        float y(long now) {
            if (fromY == toY) return toY;
            return fromY + (toY - fromY) * Easing.easeOutCubic(progress(now, moveStart));
        }

        float xOffset(long now) {
            if (departing) {
                return exitX + (-UiTokens.MESSAGE_SLIDE - exitX) * Easing.easeOutCubic(progress(now, exitStart));
            }
            float t = progress(now, entryStart);
            return t >= 1F || entryX == 0F ? 0F : entryX * (1F - UiSpring.messageEase(t));
        }

        float alpha(long now) {
            return departing ? exitAlpha * (1F - Easing.easeOutQuad(progress(now, exitStart)))
                    : Easing.easeOutQuad(progress(now, entryStart));
        }

        private void discard() {
            key = null;
            content = null;
            departing = false;
        }

        private static float progress(long now, long start) {
            if (start == Long.MIN_VALUE || now - start >= UiMotion.MESSAGE_MS) return 1F;
            return Math.max(0F, (now - start) / (float) UiMotion.MESSAGE_MS);
        }
    }

    List<Row<T>> rows() { return rows; }

    boolean retire(long now) {
        boolean departing = false;
        for (Row<T> row : rows) {
            if (!row.departing) continue;
            if (row.alpha(now) <= 0F) row.discard();
            else departing = true;
        }
        return departing;
    }

    void begin(long now, boolean animate, boolean arriving) {
        this.now = now;
        this.animate = animate;
        this.arriving = arriving;
        for (int i = 0; i < rows.size(); i++) {
            Row<T> row = rows.get(i);
            row.retained = false;
            if (row.departing && (!animate || row.alpha(now) <= 0F)) row.discard();
        }
    }

    Row<T> place(Object key, T content, float top, float entryX) {
        Row<T> available = null;
        Row<T> earliestExit = null;
        for (int i = 0; i < rows.size(); i++) {
            Row<T> row = rows.get(i);
            if (row.key == key) {
                if (!animate || row.departing) {
                    row.entryStart = Long.MIN_VALUE;
                    row.fromY = row.toY = top;
                } else if (row.toY != top) {
                    row.fromY = row.y(now);
                    row.toY = top;
                    row.moveStart = now;
                }
                row.retained = true;
                row.departing = false;
                row.content = content;
                return row;
            }
            if (available == null && row.content == null) available = row;
            if (row.departing && (earliestExit == null || row.exitStart < earliestExit.exitStart)) {
                earliestExit = row;
            }
        }
        // At most ten targets survive, so a full pool always has a prior exit to replace.
        Row<T> row = available != null ? available : earliestExit;
        row.key = key;
        row.content = content;
        row.retained = true;
        row.departing = false;
        row.fromY = row.toY = top;
        row.entryX = entryX;
        row.entryStart = animate && arriving ? now : Long.MIN_VALUE;
        return row;
    }

    void finish() {
        for (int i = 0; i < rows.size(); i++) {
            Row<T> row = rows.get(i);
            if (row.content == null || row.retained || row.departing) continue;
            if (animate && row.alpha(now) > 0F) {
                row.exitX = row.xOffset(now);
                row.exitAlpha = row.alpha(now);
                row.fromY = row.toY = row.y(now);
                row.exitStart = now;
                row.departing = true;
            } else {
                row.discard();
            }
        }
    }

    void clear() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).discard();
    }
}
