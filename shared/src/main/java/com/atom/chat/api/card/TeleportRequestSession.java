package com.atom.chat.api.card;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tracks client-adapted requests through the existing card state store. */
public final class TeleportRequestSession {
    private record Pending(String player, String source, ShareCard card) {
        String id() { return card.cardId(); }
    }
    private static final int MAX_PENDING = 100;
    private static final LinkedHashMap<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Pattern HUSK_RECEIPT = Pattern.compile(
            "^You (accepted|declined|denied) ([A-Za-z0-9_]{1,16})'s teleport request[!.]$");
    private record Attempt(Pending pending, long sentAt) {}
    private static final long RECEIPT_WINDOW_NS = 2_000_000_000L;
    private static final LinkedHashMap<String, Attempt> ATTEMPTS = new LinkedHashMap<>();

    private TeleportRequestSession() {}

    public static void receive(ShareCard card) {
        if (card.general() == null || card.general().avatarPlayerName().isBlank()) return;
        String player = card.general().avatarPlayerName().toLowerCase(Locale.ROOT);
        if (card.actions().isEmpty() && PENDING.containsKey(player)) {
            // A separate text announcement can arrive after the actionable packet.
            return;
        }
        var pending = new Pending(player, card.sourceLabel(), card);
        var previous = PENDING.put(player, pending);
        if (previous != null) close(previous, "请求已更新");
        if (PENDING.size() > MAX_PENDING) {
            Iterator<Pending> oldest = PENDING.values().iterator();
            close(oldest.next(), "请求已归档");
            oldest.remove();
        }
    }

    /** An expired, superseded or foreign card must never direct a current request. */
    public static boolean beginAction(ShareCard card) {
        if (card.general() == null) return false;
        var pending = PENDING.get(card.general().avatarPlayerName().toLowerCase(Locale.ROOT));
        if (pending == null || pending.card() != card) return false;
        long now = System.nanoTime();
        discardOldAttempts(now);
        ATTEMPTS.put(pending.id(), new Attempt(pending, now));
        PENDING.remove(pending.player());
        close(pending, "已发送传送回应");
        return true;
    }

    public static void observe(String raw, String stellarKey, String requester) {
        String text = raw.replaceAll("§.", "").strip();
        var husk = HUSK_RECEIPT.matcher(text);
        if (husk.matches()) {
            complete("HuskHomes", husk.group(2), husk.group(1).equals("accepted") ? "已接受传送请求" : "已拒绝传送请求", false);
        } else if ("stellarrtp.tpa.request_accepted".equals(stellarKey)
                || "stellarrtp.tpa.request_denied".equals(stellarKey)) {
            complete("StellarRTP", requester, stellarKey.endsWith("denied") ? "已拒绝传送请求" : "已接受传送请求", false);
        } else if ("stellarrtp.failure.request_expired".equals(stellarKey)
                || "stellarrtp.tpa.no_pending".equals(stellarKey)
                || text.equals("Error: Your pending teleport request has expired.")
                || text.equals("Error: You do not have a pending teleport request.")) {
            complete(stellarKey.startsWith("stellarrtp.") ? "StellarRTP" : "HuskHomes",
                    "", "传送请求已过期", true);
        } else if (text.equals("Teleport request accepted.") || text.equals("已接受传送请求。")) {
            complete("EssentialsX", "", "已接受传送请求", true);
        } else if (text.equals("Teleport request denied.") || text.equals("已拒绝传送请求。")) {
            complete("EssentialsX", "", "已拒绝传送请求", true);
        } else if (text.equals("Teleport request has timed out.")) {
            complete("EssentialsX", "", "传送请求已过期", true);
        }
    }

    private static void complete(String source, String player, String status, boolean localActionOnly) {
        discardOldAttempts(System.nanoTime());
        String key = player.toLowerCase(Locale.ROOT);
        Pending candidate = null;
        for (var attempt : ATTEMPTS.values()) {
            var pending = attempt.pending();
            if (!source.equals(pending.source()) || !key.isEmpty() && !key.equals(pending.player())) continue;
            if (candidate != null) return; // This receipt cannot identify a unique response.
            candidate = pending;
        }
        if (candidate == null && !key.isEmpty() && !localActionOnly) candidate = PENDING.get(key);
        if (candidate == null || !candidate.source().equals(source)) return;
        close(candidate, status);
        ATTEMPTS.remove(candidate.id());
        PENDING.remove(candidate.player(), candidate);
    }

    private static void discardOldAttempts(long now) {
        ATTEMPTS.values().removeIf(attempt -> now - attempt.sentAt() >= RECEIPT_WINDOW_NS);
    }

    private static void close(Pending pending, String status) {
        CardStateStore.update(CardState.dimmed(pending.id(), status).disableActions(true).build());
    }

    public static void reset() {
        for (var pending : PENDING.values()) close(pending, "传送请求已结束");
        PENDING.clear();
        ATTEMPTS.clear();
    }
}
