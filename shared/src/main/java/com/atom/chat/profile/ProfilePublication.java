package com.atom.chat.profile;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;

/** Publication state and stale-callback protection for one client connection. */
public final class ProfilePublication {
    private final ProfileTransport transport;
    private final EnumMap<ProfileField, Long> generations = new EnumMap<>(ProfileField.class);
    private final EnumSet<ProfileField> failed = EnumSet.noneOf(ProfileField.class);
    private final EnumSet<ProfileField> pending = EnumSet.noneOf(ProfileField.class);
    private long sequence;
    private long epoch;
    public ProfilePublication(ProfileTransport transport) { this.transport = transport; }
    public void reset() { epoch++; generations.clear(); failed.clear(); pending.clear(); }
    public boolean unsynced() { return !failed.isEmpty(); }
    public boolean syncing() { return !pending.isEmpty(); }
    private long begin(ProfileField field) {
        long generation = ++sequence;
        generations.put(field, generation);
        failed.remove(field);
        pending.add(field);
        return generation;
    }
    private boolean current(ProfileField field, long generation, long connection) {
        return epoch == connection && generations.getOrDefault(field, -1L) == generation;
    }
    public void text(ProfileField field, String value) {
        long generation = begin(field), connection = epoch;
        if (!transport.available()) { finish(field, generation, connection, "unavailable"); return; }
        transport.submit(field.patch(value), error -> transport.execute(() -> finish(field, generation, connection, error)));
    }
    public void image(ProfileField field, Path file) {
        if (field == ProfileField.SIGNATURE) throw new IllegalArgumentException("not_an_image_field");
        long generation = begin(field), connection = epoch;
        if (!transport.available()) { finish(field, generation, connection, "unavailable"); return; }
        if (transport.hostingEnabled()) {
            transport.uploadHosted(file,
                    url -> transport.execute(() -> publishImage(field, file, url, generation, connection, true)),
                    error -> transport.execute(() -> external(field, file, generation, connection)));
        } else external(field, file, generation, connection);
    }
    private void external(ProfileField field, Path file, long generation, long connection) {
        if (!current(field, generation, connection)) return;
        transport.uploadExternal(file,
                url -> transport.execute(() -> publishImage(field, file, url, generation, connection, false)),
                error -> transport.execute(() -> finish(field, generation, connection, "upload_failed")));
    }
    private void publishImage(ProfileField field, Path file, String url, long generation, long connection, boolean hosted) {
        if (!current(field, generation, connection)) return;
        transport.submit(field.patch(url), error -> transport.execute(() -> {
            if (!current(field, generation, connection)) return;
            // The temporary host may prune before metadata commits, or durable
            // storage may be full: don't publish its dead reference to peers.
            if (hosted && error != null && !error.isEmpty()) external(field, file, generation, connection);
            else finish(field, generation, connection, error);
        }));
    }
    private void finish(ProfileField field, long generation, long connection, String error) {
        if (!current(field, generation, connection)) return;
        pending.remove(field);
        boolean ok = error == null || error.isEmpty();
        if (ok) failed.remove(field); else failed.add(field);
        transport.feedback(ok);
    }
}
