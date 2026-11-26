package com.atom.chat.api.card;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Tracks client-adapted requests through the existing card state store. */
public final class TeleportRequestSession {
    private record Pending(String player, String source, ShareCard card, boolean cueAvailable) {
        String id() { return card.cardId(); }
    }
    private static final int MAX_PENDING = 100;
    private static final LinkedHashMap<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Pattern HUSK_RECEIPT = Pattern.compile(
            "^You (accepted|declined|denied) ([A-Za-z0-9_]{1,16})'s teleport request[!.]$");
    private record Attempt(Pending pending, long sentAt) {}
    private record ReceiptScope(String source, String player) {}
    private enum Receipt {
        ACCEPTED("已接受传送请求"), DENIED("已拒绝传送请求"), EXPIRED("传送请求已过期");

        final String status;
        Receipt(String status) { this.status = status; }
    }
    // A named duplicate may keep referring to an older request from the same player.
    private static final Map<ReceiptScope, EnumSet<Receipt>> NAMED_RETIRED = new HashMap<>();
    // An unnamed receipt can describe any local response to this plug-in. Retain
    // recent ambiguity without suppressing that plug-in's future receipts.
    private static final Map<String, EnumMap<Receipt, Long>> RECENT_UNNAMED = new HashMap<>();
    private static final long RECEIPT_WINDOW_NS = 2_000_000_000L;
    private static final LinkedHashMap<String, Attempt> ATTEMPTS = new LinkedHashMap<>();
    private record CueKey(String player, String source, String title) {}
    private static final LinkedHashMap<CueKey, Long> RECENT_CUES = new LinkedHashMap<>();

    private TeleportRequestSession() {}

    public static void receive(ShareCard card) {
        if (card.general() == null || card.general().avatarPlayerName().isBlank()) return;
        String player = card.general().avatarPlayerName().toLowerCase(Locale.ROOT);
        if (card.actions().isEmpty() && PENDING.containsKey(player)) {
            // A separate text announcement can arrive after the actionable packet.
            return;
        }
        var pending = new Pending(player, card.sourceLabel(), card, true);
        var previous = PENDING.put(player, pending);
        if (previous != null) close(previous, "请求已更新");
        if (PENDING.size() > MAX_PENDING) {
            Iterator<Pending> oldest = PENDING.values().iterator();
            close(oldest.next(), "请求已归档");
            oldest.remove();
        }
    }

    /** Called only after live storage. The pending identity comes from the native request adapter. */
    public static boolean claimIncomingCue(ShareCard card, String ownName) {
        return claimIncomingCue(card, ownName, System.nanoTime());
    }

    static boolean claimIncomingCue(ShareCard card, String ownName, long now) {
        if (card == null || card.general() == null) return false;
        String player = card.general().avatarPlayerName().toLowerCase(Locale.ROOT);
        if (player.equalsIgnoreCase(ownName)) return false;
        var pending = PENDING.get(player);
        if (pending == null || pending.card() != card || !pending.cueAvailable()) return false;
        PENDING.put(player, new Pending(player, pending.source(), card, false));
        CueKey key = new CueKey(player, pending.source(), card.title().toLowerCase(Locale.ROOT));
        var previous = RECENT_CUES.get(key);
        if (previous != null && now - previous < RECEIPT_WINDOW_NS) return false;
        RECENT_CUES.remove(key);
        RECENT_CUES.put(key, now);
        if (RECENT_CUES.size() > MAX_PENDING) RECENT_CUES.remove(RECENT_CUES.keySet().iterator().next());
        return true;
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
        if (text.startsWith(TeleportRequestMatcher.STELLAR_PREFIX)) {
            String body = text.substring(TeleportRequestMatcher.STELLAR_PREFIX.length()).stripLeading();
            if (body.equals("传送请求已拒绝。") || body.equals("Teleport request denied.")) {
                complete("StellarRTP", "", Receipt.DENIED);
            } else if (body.equals("传送请求已过期。") || body.equals("Teleport request expired.")
                    || body.equals("你当前没有待处理的传送请求。") || body.equals("You have no pending teleport request.")) {
                complete("StellarRTP", "", Receipt.EXPIRED);
            }
        } else if (husk.matches()) {
            complete("HuskHomes", husk.group(2), husk.group(1).equals("accepted") ? Receipt.ACCEPTED : Receipt.DENIED);
        } else if ("stellarrtp.tpa.request_accepted".equals(stellarKey)
                || "stellarrtp.tpa.request_denied".equals(stellarKey)) {
            complete("StellarRTP", requester, stellarKey.endsWith("denied") ? Receipt.DENIED : Receipt.ACCEPTED);
        } else if ("stellarrtp.failure.request_expired".equals(stellarKey)
                || "stellarrtp.tpa.no_pending".equals(stellarKey)
                || text.equals("Error: Your pending teleport request has expired.")
                || text.equals("Error: You do not have a pending teleport request.")) {
            complete(stellarKey.startsWith("stellarrtp.") ? "StellarRTP" : "HuskHomes",
                    "", Receipt.EXPIRED);
        } else if (text.equals("Teleport request accepted.") || text.equals("已接受传送请求。")) {
            complete("EssentialsX", "", Receipt.ACCEPTED);
        } else if (text.equals("Teleport request denied.") || text.equals("已拒绝传送请求。")) {
            complete("EssentialsX", "", Receipt.DENIED);
        } else if (text.equals("Teleport request has timed out.")) {
            complete("EssentialsX", "", Receipt.EXPIRED);
        }
    }

    private static void complete(String source, String player, Receipt receipt) {
        long now = System.nanoTime();
        discardOldAttempts(now);
        String key = player.toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            var recent = RECENT_UNNAMED.get(source);
            Long retiredAt = recent == null ? null : recent.get(receipt);
            if (retiredAt != null && now - retiredAt < RECEIPT_WINDOW_NS) return;
        } else {
            var retired = NAMED_RETIRED.get(new ReceiptScope(source, key));
            if (retired != null && retired.contains(receipt)) return;
        }

        Pending candidate = null;
        for (var attempt : ATTEMPTS.values()) {
            var pending = attempt.pending();
            if (!source.equals(pending.source()) || !key.isEmpty() && !key.equals(pending.player())) continue;
            if (candidate != null) return;
            candidate = pending;
        }
        if (candidate == null) return;
        close(candidate, receipt.status);
        String completedPlayer = candidate.player();
        RECENT_CUES.keySet().removeIf(cue -> cue.player().equals(completedPlayer) && cue.source().equals(source));
        ATTEMPTS.remove(candidate.id());
        retire(candidate, EnumSet.of(receipt), now);
    }

    private static void discardOldAttempts(long now) {
        var entries = ATTEMPTS.values().iterator();
        while (entries.hasNext()) {
            var attempt = entries.next();
            if (now - attempt.sentAt() >= RECEIPT_WINDOW_NS) {
                // Use the actual expiry, even if the next action prunes this much later.
                retire(attempt.pending(), EnumSet.allOf(Receipt.class), attempt.sentAt() + RECEIPT_WINDOW_NS);
                entries.remove();
            }
        }
    }

    private static void retire(Pending pending, EnumSet<Receipt> receipts, long retiredAt) {
        var named = new ReceiptScope(pending.source(), pending.player());
        NAMED_RETIRED.computeIfAbsent(named, key -> EnumSet.noneOf(Receipt.class)).addAll(receipts);
        var recent = RECENT_UNNAMED.computeIfAbsent(pending.source(), key -> new EnumMap<>(Receipt.class));
        for (var receipt : receipts) recent.merge(receipt, retiredAt, Math::max);
    }

    private static void close(Pending pending, String status) {
        CardStateStore.update(CardState.dimmed(pending.id(), status).disableActions(true).build());
    }

    public static void reset() {
        for (var pending : PENDING.values()) close(pending, "传送请求已结束");
        PENDING.clear();
        ATTEMPTS.clear();
        NAMED_RETIRED.clear();
        RECENT_UNNAMED.clear();
        RECENT_CUES.clear();
    }
}
