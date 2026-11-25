package com.atom.chat.trchat;

import java.util.Arrays;
import java.util.UUID;

/** Standalone verification for the pinned bridge artifact, without loading Bukkit. */
public final class ProxyEnvelopeCheck {
    public static void main(String[] args) {
        byte[] key = new byte[32];
        byte[] stranger = new byte[32];
        stranger[0] = 1;
        String[] source = {"BroadcastRaw", UUID.randomUUID().toString(), "{\"text\":\"exact\"}",
                "", "true", "", "fallback", "A", ""};
        var authority = new ProxyEnvelope.Origin("east", "global", UUID.randomUUID());
        String[] signed = ProxyEnvelope.seal(source, authority, key, 30_000);
        expect(!ProxyEnvelope.isMarked(source), "ordinary TrChat frame can use its original route");
        expect(ProxyEnvelope.isMarked(signed), "bridged frame requires authorization even if verification fails");
        expect(ProxyEnvelope.isMarked(Arrays.copyOf(signed, 14)), "incomplete bridge frame still requires authorization");
        expect(authority.equals(ProxyEnvelope.open(signed, key, 30_100)), "valid source");
        expect(ProxyEnvelope.open(source, key, 30_100) == null, "no bridge proof");
        expect(ProxyEnvelope.open(signed, stranger, 30_100) == null, "untrusted backend");
        expect(ProxyEnvelope.open(signed, key, 51_000) == null, "expired");
        expect(ProxyEnvelope.open(signed, key, 27_000) == null, "timestamp too far in the future");
        for (int field : new int[]{1, 2, 3, 4, 5, 7, 10, 11, 12, 13}) {
            String[] tampered = Arrays.copyOf(signed, signed.length);
            tampered[field] += "X";
            expect(ProxyEnvelope.open(tampered, key, 30_100) == null, "tampered field " + field);
        }
        System.out.println("Proxy envelope: expected HMAC, original-component, route and freshness checks passed");
    }

    private static void expect(boolean pass, String check) {
        if (!pass) throw new AssertionError(check);
    }
}
