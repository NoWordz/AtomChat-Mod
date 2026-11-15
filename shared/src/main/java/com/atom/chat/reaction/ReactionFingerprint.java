package com.atom.chat.reaction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
/** Fingerprints use only fields preserved by the packet codec. */
public final class ReactionFingerprint {
    private ReactionFingerprint() {}
    /** Wire-content matching fingerprint, never a message identity. */
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
