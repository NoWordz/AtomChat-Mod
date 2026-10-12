package com.atom.chat.reaction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
/** Only fields preserved by the packet codec participate; unsigned collisions are revoked by the ledger. */
public final class ReactionFingerprint {
    private ReactionFingerprint() {}
    /**
     * Content key both endpoints can compute from what they actually see:
     * the sender's visible name plus the visible body. A server that rewrites
     * chat into a system/game line (offline mode, LAN, plugins) drops the signed
     * body entirely, so the signature-based {@link #of} key can never be rebuilt
     * on the client. This is deliberately weaker (no signature, no timestamp):
     * it trades per-message cryptographic identity for "works on every server",
     * which is the same trade the plain-text quote prefix already makes.
     */
    public static UUID contentKey(String author,String body) {
        String canonical="content|"+(author==null?"":author.trim())+"|"+(body==null?"":body);
        try {
            ByteBuffer hash=ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new UUID(hash.getLong(),hash.getLong());
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
    public static UUID of(UUID author,byte[] signature,Instant timestamp,long salt,String body) {
        String canonical=author+"|"+(signature==null
                ? "unsigned|"+timestamp.toEpochMilli()+"|"+salt+"|"+body
                : "signed|"+Base64.getEncoder().encodeToString(signature));
        try {
            ByteBuffer hash=ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new UUID(hash.getLong(),hash.getLong());
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
}
