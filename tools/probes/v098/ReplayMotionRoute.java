package atomchatprobe;

import com.atom.chat.nav.AppPage;
import com.atom.chat.screen.ScreenMetrics;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.HeaderChrome;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.class_310;
import net.minecraft.client.gui.screen.AtomChatScreen;

public final class ReplayMotionRoute {
    static final boolean ENABLED = Boolean.getBoolean("atomchat.replay.motionTrace");
    private static final long STEP_NANOS = 540_000_000;
    private static long actionId, lastAction;
    private static int step;
    private static boolean active, tracing;

    private ReplayMotionRoute() {}

    static void reset(String scene) {
        active = scene.equals("media") || scene.equals("text");
        tracing = active || scene.equals("appearance");
        step = 0;
        lastAction = System.nanoTime();
    }

    static boolean showingList() {
        return active && step < 14 && step % 7 == 2;
    }

    static boolean tick(class_310 client, AtomChatScreen screen, String scene, long now) {
        if (!active) return false;
        if (step >= 14 || now - lastAction < STEP_NANOS) return true;
        switch (step % 7) {
            case 0 -> {
                if (screen.topPage() != AppPage.WORLD_CHAT) screen.openWorldChat();
                require(screen.topPage() == AppPage.WORLD_CHAT, "public chat entry");
            }
            case 1 -> {
                var header = readFields(HeaderChrome.class, screen, Fields.HEADER);
                click(client, screen, header.backButton(), "public-back-to-list");
                require(screen.topPage() == AppPage.CHAT_LIST, "back click reached the list");
            }
            case 2 -> {
                screen.openWorldChat();
                require(screen.topPage() == AppPage.WORLD_CHAT, "public chat reopened");
            }
            case 3 -> {
                click(client, screen, layout(screen).emojiBtn, "open-emoji");
                require(emoji(screen).isOpen(), "emoji button opened the panel");
            }
            case 4 -> {
                if (!ReplaySlowClick.verify()) return true;
                clickStickerTab(client, screen, 0, "emoji-tab");
                require(emoji(screen).diagnosticTab() == 0, "emoji tab selected");
            }
            case 5 -> {
                clickStickerTab(client, screen, 2, "sticker-tab");
                require(emoji(screen).diagnosticTab() == 2, "sticker tab selected");
            }
            case 6 -> {
                var bounds = emoji(screen).bounds(layout(screen));
                float pad = UiTokens.EMOJI_PANEL_PAD;
                float width = (bounds.w() - 2 * pad) / UiTokens.EMOTE_COLS;
                click(client, screen, bounds.x() + pad + width / 2,
                        bounds.y() + UiTokens.EMOJI_TAB_H + UiTokens.s(2) + UiTokens.EMOTE_CELL / 2,
                        "select-first-sticker");
                require(!emoji(screen).isOpen(), "selecting the fixture sticker closed the panel");
            }
            default -> throw new AssertionError(step);
        }
        lastAction = now;
        if (++step == 14) System.out.println("ATOMCHAT_MOTION_ROUTE_COMPLETE " + scene);
        return true;
    }

    private static void clickStickerTab(class_310 client, AtomChatScreen screen, int tab, String label) {
        var bounds = emoji(screen).bounds(layout(screen));
        float pad = UiTokens.EMOJI_PANEL_PAD;
        float width = (bounds.w() - 2 * pad) / 3;
        click(client, screen, bounds.x() + pad + (tab + 0.5F) * width,
                bounds.y() + UiTokens.EMOJI_TAB_H / 2, label);
    }

    private static void click(class_310 client, AtomChatScreen screen, UiLayout.Rect rect, String label) {
        click(client, screen, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2, label);
    }

    private static void click(class_310 client, AtomChatScreen screen, float x, float y, String label) {
        var window = client.method_22683();
        double guiX = x / ScreenMetrics.toVirtualX(window, 1);
        double guiY = y / ScreenMetrics.toVirtualY(window, 1);
        if (label.equals("open-emoji")) ReplaySlowClick.arm((int) guiX, (int) guiY);
        var event = new Action();
        event.actionId = ++actionId;
        event.name = label;
        event.startedNanos = System.nanoTime();
        screen.method_25402(guiX, guiY, 0);
        event.pressNanos = System.nanoTime() - event.startedNanos;
        screen.method_25406(guiX, guiY, 0);
        event.releaseNanos = System.nanoTime() - event.startedNanos - event.pressNanos;
        event.commit();
    }

    private static UiLayout layout(AtomChatScreen screen) {
        try {
            return (UiLayout) Fields.LAYOUT.invoke(screen);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the replay layout", failure);
        }
    }

    private static EmojiPanel emoji(AtomChatScreen screen) {
        return readFields(EmojiPanel.class, screen, Fields.EMOJI);
    }

    private static <T> T readFields(Class<T> type, Object value, Field field) {
        try {
            return type.cast(field.get(value));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read the replay fixture field", failure);
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }

    private static final class Fields {
        static final Field HEADER = field(AtomChatScreen.class, "headerChrome");
        static final Field EMOJI = field(AtomChatScreen.class, "emojiPanel");
        static final Method LAYOUT = method(AtomChatScreen.class);

        static Field field(Class<?> owner, String name) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind " + name, failure);
            }
        }

        static Method method(Class<?> owner) {
            try {
                Method method = owner.getDeclaredMethod("layout");
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind the live layout", failure);
            }
        }
    }

    public static void call(String name, long startedNanos) {
        long elapsedNanos = System.nanoTime() - startedNanos;
        if (!tracing || elapsedNanos < 100_000) return;
        var event = new Call();
        event.name = name;
        event.actionId = actionId;
        event.startedNanos = startedNanos;
        event.elapsedNanos = elapsedNanos;
        event.commit();
    }

    @Name("bench.AtomChatMotionAction")
    @StackTrace(false)
    static final class Action extends Event {
        long actionId, startedNanos, pressNanos, releaseNanos;
        String name;
    }

    @Name("bench.AtomChatMotionCall")
    @StackTrace(false)
    static final class Call extends Event {
        long actionId, startedNanos, elapsedNanos;
        String name;
    }
}
