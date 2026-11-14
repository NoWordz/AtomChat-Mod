package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;

import java.util.List;

/**
 * Blocked-player matching. The list is global, persisted in
 * AtomChatConfig.blockedPlayers, and matched by real profile name (case- and
 * section-sign-insensitive). Display/decorated names are accepted as fallback
 * so nickname servers can still be blocked from a card that only had a title.
 */
public final class BlockList {
    private BlockList() {
    }

    public static boolean isBlocked(PlayerRef player) {
        if (player == null) {
            return false;
        }
        if(com.atom.chat.onboarding.TutorialSandbox.active())return com.atom.chat.onboarding.TutorialSandbox.blocked(player.realName());
        return isBlocked(player.realName());
    }

    public static boolean isBlocked(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return matches(name, AtomChatConfig.get().blockedPlayers);
    }

    public static boolean matches(String name, List<String> blocked) {
        if (name == null || blocked == null || blocked.isEmpty()) {
            return false;
        }
        String cleaned = clean(name);
        if (cleaned == null) {
            return false;
        }
        for (String b : blocked) {
            String candidate = clean(b);
            if (candidate != null && candidate.equalsIgnoreCase(cleaned)) {
                return true;
            }
        }
        return false;
    }

    public static void setBlocked(PlayerRef player, boolean blocked) {
        if(com.atom.chat.onboarding.TutorialSandbox.active()){if(player!=null)com.atom.chat.onboarding.TutorialSandbox.block(player.realName(),blocked);return;}
        if (player == null) {
            return;
        }
        AtomChatConfig config = AtomChatConfig.get();
        java.util.ArrayList<String> list = config.blockedPlayers == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(config.blockedPlayers);
        String name = clean(player.realName());
        if (name == null) {
            return;
        }
        boolean present = false;
        for (int i = 0; i < list.size(); i++) {
            String candidate = clean(list.get(i));
            if (candidate != null && candidate.equalsIgnoreCase(name)) {
                present = true;
                if (!blocked) {
                    list.remove(i);
                }
                break;
            }
        }
        if (blocked && !present) {
            list.add(name);
        }
        config.blockedPlayers = List.copyOf(list);
        AtomChatConfig.save(config);
    }

    public static boolean isBlocked(ChatMessage message) {
        if (message == null || message.isSystem()) {
            return false;
        }
        String profile = message.getProfileName();
        return profile != null && isBlocked(profile);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
