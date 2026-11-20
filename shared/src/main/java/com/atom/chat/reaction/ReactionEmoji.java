package com.atom.chat.reaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
public final class ReactionEmoji {
    private ReactionEmoji() {}
    private static final List<String> FACES = List.of(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😋", "😛", "😜", "🤪", "😎", "🤗", "🤔", "😐",
            "😢", "😭", "😤", "😡", "🥺", "😴", "😷", "🤒");
    private static final List<String> MORE_FACES = List.of(
            "🙃", "😌", "😏", "😒", "🙄", "😬", "😶", "🤐",
            "🤨", "🧐", "🤓", "🤭", "🤫", "🤥", "🫢", "🫣",
            "🫠", "🥹", "😳", "😔", "😞", "😖", "😩", "😫",
            "😮", "😯", "😲", "😱", "😨", "😰", "😓", "😥",
            "😪", "🤤", "😵", "🤯", "🥴", "🤢", "🤮", "🤧",
            "🥵", "🥶", "🥱", "😦", "😈", "👿", "🤬", "🤠",
            "🥳", "🤑", "🤕", "🫥", "🫡");
    private static final List<String> MEMES = List.of("🤡", "💀", "🗿", "👀");
    private static final List<String> MORE_MEMES = List.of("☠️", "💩", "👻", "👽", "🤖", "👁️", "🧌");
    private static final List<String> OTHER = List.of(
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
            "💯", "💢", "💬", "💭");

    public static final List<String> ALL = join(FACES, MORE_FACES, MEMES, MORE_MEMES, OTHER);
    private static final Set<String> ALLOWED = Set.copyOf(ALL);
    private static final Set<String> LEGACY = Set.copyOf(join(FACES, MEMES, OTHER));

    @SafeVarargs
    private static List<String> join(List<String>... groups) {
        List<String> all = new ArrayList<>();
        for (List<String> group : groups) all.addAll(group);
        return List.copyOf(all);
    }

    public static boolean allowed(String emoji) { return ALLOWED.contains(emoji); }
    public static boolean legacy(String emoji) { return LEGACY.contains(emoji); }
}
