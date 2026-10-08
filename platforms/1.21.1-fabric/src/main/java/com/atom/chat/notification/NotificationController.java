package com.atom.chat.notification;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Turns capture-side events (mentions, quote replies, incoming whispers) into
 * notification side effects. The banner is Skia-rendered only while the
 * AtomChat panel is open; the sound always respects its own toggle and a 2s
 * dedupe gate.
 */
public final class NotificationController {
    private static long lastSoundMs = NotificationSoundGate.NEVER;
    /** Poke cue dedupe, deliberately separate so a poke never mutes a mention. */
    private static long lastPokeMs = Long.MIN_VALUE;

    /**
     * Bundled notification cue. Must match {@code sounds.json}'s local key
     * ({@code "notification"}), not {@code "atomchat.notification"} — the JSON
     * keys are already scoped by the {@code assets/atomchat/} namespace.
     */
    public static final SoundEvent NOTIFICATION_SOUND =
            SoundEvent.of(Identifier.of("atomchat", "notification"));

    private NotificationController() {
    }

    /**
     * Standard Fabric sound-event registration — without it the event is an
     * unregistered instance whose behaviour across the sound engine is
     * undefined. Call once during client init, before any resource reload.
     */
    public static void registerSound() {
        Registry.register(Registries.SOUND_EVENT,
                NOTIFICATION_SOUND.getId(), NOTIFICATION_SOUND);
    }

    /** Plays the cue directly, bypassing the gate — the settings "test sound" card. */
    public static void playTestSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        float volume = Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().notifyVolume));
        // PositionedSoundInstance.master(SoundEvent, pitch, volume) — in 1.21.1
        // the second float is pitch and the third is volume. Passing notifyVolume
        // as the second float made the slider change pitch at full volume.
        debug("playTestSound volume=" + volume);
        client.getSoundManager().play(PositionedSoundInstance.master(NOTIFICATION_SOUND, 1.0F, volume));
    }

    /**
     * The poke cue: the same bundled pop pitched up, so a poke reads as its own
     * "hey" without a second asset. Its own clock keeps a poke from muting a
     * mention cue and vice versa.
     */
    public static void playPokeSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastPokeMs != Long.MIN_VALUE && now - lastPokeMs < com.atom.chat.poke.PokeGate.MIN_INTERVAL_MS) {
            return;
        }
        lastPokeMs = now;
        float volume = Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().notifyVolume));
        client.getSoundManager().play(PositionedSoundInstance.master(NOTIFICATION_SOUND, 1.25F, volume));
    }

    public static void onMention(ChatMessage message) {
        fire(NotificationBanner.Type.MENTION, message);
    }

    public static void onQuote(ChatMessage message) {
        fire(NotificationBanner.Type.QUOTE, message);
    }

    public static void onWhisper(ChatMessage message) {
        fire(NotificationBanner.Type.WHISPER, message);
    }

    private static void fire(NotificationBanner.Type type, ChatMessage message) {
        if (message == null || message.isOwn()) {
            debug("fire skipped type=" + type + " message=" + (message == null ? "null" : "own"));
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean soundOn = NotificationBanner.soundEnabled(type);
        debug("fire type=" + type
                + " soundOn=" + soundOn
                + " player=" + (client != null && client.player != null)
                + " world=" + (client != null && client.world != null)
                + " screen=" + (client != null && client.currentScreen != null
                        ? client.currentScreen.getClass().getSimpleName() : "null"));
        if (soundOn) {
            playSound(type);
        }
        // Queue globally, render panel-only: opening the panel inside the 4s
        // window must reveal the banner, so enqueueing cannot depend on the
        // screen being open right now.
        boolean canQueue = NotificationBanner.enabled(type)
                && client != null && client.world != null;
        if (canQueue) {
            String sender = message.getSenderName();
            if (sender == null || sender.isBlank()) {
                sender = message.getProfileName();
            }
            NotificationBanner.INSTANCE.enqueue(type, sender, message.getDisplayText(), message);
        }
    }

    private static void playSound(NotificationBanner.Type type) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            debug("playSound skipped type=" + type + " client=" + (client != null)
                    + " player=" + (client != null && client.player != null));
            return;
        }
        long now = System.currentTimeMillis();
        boolean allowed = NotificationSoundGate.shouldPlay(now, lastSoundMs);
        debug("playSound type=" + type + " allowed=" + allowed
                + " lastSoundMs=" + lastSoundMs + " now=" + now);
        if (!allowed) {
            return;
        }
        lastSoundMs = now;
        float volume = Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().notifyVolume));
        debug("playSound playing type=" + type + " volume=" + volume);
        // PositionedSoundInstance.master(SoundEvent, pitch, volume) — see
        // playTestSound() for the same parameter-order note.
        client.getSoundManager().play(PositionedSoundInstance.master(NOTIFICATION_SOUND, 1.0F, volume));
    }

    /** Debug-only diagnostics; silent unless the About-page debug switch is on. */
    private static void debug(String message) {
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info("[notify] {}", message);
        }
    }
}
