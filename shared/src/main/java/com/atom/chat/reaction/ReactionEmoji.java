package com.atom.chat.reaction;
import java.util.List;
import java.util.Set;
public final class ReactionEmoji {
    private ReactionEmoji() {}
    private static final String[] VALUES = {
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😋", "😛", "😜", "🤪", "😎", "🤗", "🤔", "😐",
            "😢", "😭", "😤", "😡", "🥺", "😴", "😷", "🤒",
            "🐱", "🐶", "🐼", "🐨", "🐰", "🦊", "🐸", "🐵",
            "🐭", "🐹", "🐮", "🦁", "🐯", "🐻", "🐧", "🐤",
            "🐴", "🦄", "🐝", "🐞", "🦋", "🐙", "🦀", "🐠",
            "🐷", "🐖",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
            "💕", "💖", "💗", "💘", "💝", "💟", "❣️", "💌",
            "👍", "👎", "👏", "🙌", "💪", "🤝", "👋", "✌️",
            "🎮", "🎯", "🎨", "🎵", "🎶", "🎤", "🎧", "🎼",
            "⭐", "🌟", "🔥", "💧", "🌈", "❄️", "🎉", "🎊",
            "🍕", "🍔", "🌮", "🍩", "🍪", "🎂", "☕", "🍺",
            "⬆️", "⬇️", "✅", "❌", "❓", "❗", "💤", "💡",
            "💀", "🗿", "🤡", "👀", "💯", "💢", "💬", "💭",
    };
    public static final List<String> ALL = List.of(VALUES);
    private static final Set<String> ALLOWED = Set.copyOf(ALL);
    public static boolean allowed(String emoji) { return ALLOWED.contains(emoji); }
}
