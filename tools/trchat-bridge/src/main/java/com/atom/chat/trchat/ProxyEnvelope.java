package com.atom.chat.trchat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class ProxyEnvelope {
    private static final String MARKER = "atomchat-channel-1";
    private ProxyEnvelope() {}
    record Origin(String backend, String channel, UUID deliveryId) {}

    static String[] seal(String[] raw, Origin origin, byte[] key, long timestamp) {
        if (raw.length != 9 || !"BroadcastRaw".equals(raw[0]) || !"true".equals(raw[4])) return raw;
        String[] frame = Arrays.copyOf(raw, 15);
        frame[9] = MARKER;
        frame[10] = origin.backend();
        frame[11] = origin.channel();
        frame[12] = origin.deliveryId().toString();
        frame[13] = Long.toString(timestamp);
        frame[14] = Base64.getUrlEncoder().withoutPadding().encodeToString(tag(frame, key));
        return frame;
    }

    static boolean isMarked(String[] frame) {
        return frame.length > 9 && MARKER.equals(frame[9]);
    }

    static Origin open(String[] frame, byte[] key, long now) {
        if (frame.length != 15 || !"BroadcastRaw".equals(frame[0]) || !"true".equals(frame[4])
                || !isMarked(frame) || frame[14].length() != 43) return null;
        try {
            long timestamp = Long.parseLong(frame[13]);
            if (timestamp < now - 20_000 || timestamp > now + 2_000
                    || !MessageDigest.isEqual(tag(frame, key), Base64.getUrlDecoder().decode(frame[14]))) return null;
            return new Origin(frame[10], frame[11], UUID.fromString(frame[12]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] tag(String[] frame, byte[] key) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            for (int i = 0; i < 14; i++) {
                byte[] field = frame[i].getBytes(StandardCharsets.UTF_8);
                out.writeInt(field.length);
                out.write(field);
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(bytes.toByteArray());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
