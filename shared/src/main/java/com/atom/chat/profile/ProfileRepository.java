package com.atom.chat.profile;

import com.atom.chat.net.MediaIds;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.UUID;

/** World-scoped durable documents and copies of their hosted image bytes. */
public final class ProfileRepository {
    private static final Gson JSON = new Gson();
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private final Path documents;
    private final Path images;
    private final Path temporaryMedia;
    private long maxBytes;
    public ProfileRepository(Path root, Path temporaryMedia, long maxBytes) {
        this.documents = root.resolve("documents");
        this.images = root.resolve("images");
        this.temporaryMedia = temporaryMedia;
        this.maxBytes = Math.max(0, maxBytes);
        recoverOwnedTransactions();

    }
    public synchronized ProfileRecord get(UUID uuid) throws IOException {
        if (uuid == null) throw new IllegalArgumentException("missing_uuid");
        Path file = documents.resolve(uuid + ".json");
        if (!Files.isRegularFile(file)) return ProfileRecord.empty(uuid);
        if (Files.size(file) > 16384) throw new IOException("oversize_profile_document");
        try {
            ProfileRecord data = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ProfileRecord.class);
            if (data == null || !uuid.equals(data.uuid()) || data.revision() < 0)
                throw new IllegalArgumentException("invalid_profile_document");
            validateSignature(data.signature());
            ProfileReferences.validate(data.avatar());
            ProfileReferences.validate(data.banner());
            if (data.signature() == null || data.avatar() == null || data.banner() == null)
                throw new IllegalArgumentException("missing_profile_fields");
            return data;
        } catch (RuntimeException e) { throw new IOException("invalid_profile_document", e); }
    }
    public synchronized ProfileRecord update(UUID uuid, ProfilePatch patch) throws IOException {
        if (patch == null) throw new IllegalArgumentException("missing_patch");
        validateSignature(patch.signature());
        ProfileReferences.validate(patch.avatar());
        ProfileReferences.validate(patch.banner());
        ProfileRecord old = get(uuid);
        String signature = patch.signature() == null ? old.signature() : patch.signature();
        String avatar = patch.avatar() == null ? old.avatar() : patch.avatar();
        String banner = patch.banner() == null ? old.banner() : patch.banner();
        int imageFields = old.imageFields() | (patch.avatar() != null ? 1 : 0) | (patch.banner() != null ? 2 : 0);
        if (signature.equals(old.signature()) && avatar.equals(old.avatar()) && banner.equals(old.banner())
                && imageFields == old.imageFields()) {
            // A no-op still checks/preserves hosted resources after legacy migration.
            preserve(avatar); preserve(banner);
            return old;
        }
        Files.createDirectories(documents);
        try {
            preserve(avatar);
            preserve(banner);
            ProfileRecord next = new ProfileRecord(uuid, signature, avatar, banner, Math.addExact(old.revision(), 1), imageFields);
            Files.createDirectories(documents);
            Path file = documents.resolve(uuid + ".json");
            Path pending = Files.createTempFile(documents, uuid + "-", ".tmp");
            try {
                Files.writeString(pending, JSON.toJson(next), StandardCharsets.UTF_8);
                replace(pending, file);
            } finally { Files.deleteIfExists(pending); }
            if (!avatar.equals(old.avatar()) || !banner.equals(old.banner())) cleanupAfterPublication();
            return next;
        } catch (IOException | RuntimeException e) {
            // Newly copied orphan bytes must not exhaust the quota after failed publication.
            cleanupAfterPublication();
            throw e;
        }
    }
    private static void validateSignature(String value) {
        if (value != null && value.length() > 256) throw new IllegalArgumentException("signature_too_long");
    }
    private void preserve(String reference) throws IOException {
        String id = MediaIds.idOf(reference);
        if (id == null) return;
        Path target = images.resolve(id);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
        Path source = temporaryMedia.resolve(id);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("missing_hosted_image");
        long size = Files.size(source);
        if (size <= 0 || size > MAX_FILE_BYTES) throw new IOException("profile_image_too_large");
        long used = 0;
        if (Files.isDirectory(images)) {
            try (var stream = Files.list(images)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) used += Files.size(file);
            }
        }
        if (size > maxBytes || used > maxBytes - size) throw new IOException("profile_storage_full");
        byte[] bytes = Files.readAllBytes(source);
        String extension = MediaIds.extensionOf(bytes);
        if (extension == null || !id.equals(MediaIds.idFor(bytes, extension))) throw new IOException("invalid_hosted_image");
        Files.createDirectories(images);
        Path pending = Files.createTempFile(images, "image-", ".tmp");
        try {
            Files.write(pending, bytes);
            replace(pending, target);
        } finally { Files.deleteIfExists(pending); }
    }
    public synchronized void setMaxBytes(long bytes) { maxBytes = Math.max(0, bytes); }
    public synchronized Path media(String id) {
        if (!MediaIds.isSafeId(id)) return null;
        Path file = images.resolve(id);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? file : null;
    }
    private void recoverOwnedTransactions() {
        for (Path directory : new Path[]{images, documents}) {
            if (!Files.isDirectory(directory)) continue;
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    boolean ours = directory.equals(images) ? name.matches("image-\\d+\\.tmp")
                            : name.matches("[0-9a-f-]{36}-\\d+\\.tmp");
                    if (ours) Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                        "profile", "Failed to recover abandoned profile transaction files"), e);
            }
        }
    }

    private void cleanupAfterPublication() {
        try { discardUnreferencedImages(); }
        catch (IOException | RuntimeException e) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "profile", "Failed to clean unreferenced profile images"), e);
        }
    }
    private void discardUnreferencedImages() throws IOException {
        if (!Files.isDirectory(images) || !Files.isDirectory(documents)) return;
        var referenced = new HashSet<String>();
        try (var stream = Files.list(documents)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                // Corrupt documents abort cleanup; deleting their images would destroy recovery data.
                ProfileRecord record = get(UUID.fromString(name.substring(0, name.length() - 5)));
                for (String reference : new String[]{record.avatar(), record.banner()}) {
                    String id = MediaIds.idOf(reference);
                    if (id != null) referenced.add(id);
                }
            }
        }
        try (var stream = Files.list(images)) {
            for (Path file : stream.toList()) {
                String id = file.getFileName().toString();
                if (MediaIds.isSafeId(id) && !referenced.contains(id)) Files.deleteIfExists(file);
            }
        }
    }
    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
