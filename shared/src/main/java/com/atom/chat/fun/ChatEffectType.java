package com.atom.chat.fun;
/** Emoji identity; particles and matching share the same vocabulary. */
public enum ChatEffectType {
    HEART("❤️"), CAKE("🎂"), FIREWORK("🎆"), STAR("⭐");
    private final String emoji;
    ChatEffectType(String emoji) { this.emoji = emoji; }
    public String emoji() { return emoji; }
}
