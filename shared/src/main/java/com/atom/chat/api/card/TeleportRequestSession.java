package com.atom.chat.api.card;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private record ReceiptScope(String source, String player) {}
    private enum Receipt {
        ACCEPTED("已接受传送请求"), DENIED("已拒绝传送请求"), EXPIRED("传送请求已过期");

        final String status;
        Receipt(String status) { this.status = status; }
    }
    // The plug-ins do not provide a request token in these receipts. Preserve which
    // replies older generations could produce even after the short action window.
    private static final Map<ReceiptScope, EnumSet<Receipt>> RETIRED = new HashMap<>();
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
        discardOldAttempts(System.nanoTime());
        String key = player.toLowerCase(Locale.ROOT);
        var retired = RETIRED.get(new ReceiptScope(source, key));
        if (retired != null && retired.contains(receipt)) return;

        Pending candidate = null;
        for (var attempt : ATTEMPTS.values()) {
            var pending = attempt.pending();
            if (!source.equals(pending.source()) || !key.isEmpty() && !key.equals(pending.player())) continue;
            if (candidate != null) return;
            candidate = pending;
        }
        if (candidate == null) return;
        close(candidate, receipt.status);
        ATTEMPTS.remove(candidate.id());
        retire(candidate, EnumSet.of(receipt));
    }

    private static void discardOldAttempts(long now) {
        var entries = ATTEMPTS.values().iterator();
        while (entries.hasNext()) {
            var attempt = entries.next();
            if (now - attempt.sentAt() >= RECEIPT_WINDOW_NS) {
                retire(attempt.pending(), EnumSet.allOf(Receipt.class));
                entries.remove();
            }
        }
    }

    private static void retire(Pending pending, EnumSet<Receipt> receipts) {
        var named = new ReceiptScope(pending.source(), pending.player());
        var unnamed = new ReceiptScope(pending.source(), "");
        RETIRED.computeIfAbsent(named, key -> EnumSet.noneOf(Receipt.class)).addAll(receipts);
        RETIRED.computeIfAbsent(unnamed, key -> EnumSet.noneOf(Receipt.class)).addAll(receipts);
    }

    private static void close(Pending pending, String status) {
        CardStateStore.update(CardState.dimmed(pending.id(), status).disableActions(true).build());
    }

    public static void reset() {
        for (var pending : PENDING.values()) close(pending, "传送请求已结束");
        PENDING.clear();
        ATTEMPTS.clear();
        RETIRED.clear();
    }
}
