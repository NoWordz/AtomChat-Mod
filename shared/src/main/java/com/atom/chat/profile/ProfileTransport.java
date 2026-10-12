package com.atom.chat.profile;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
/** Loader boundary; installed on clients only. */
public interface ProfileTransport {
    boolean available();
    boolean hostingEnabled();
    void request(UUID uuid);
    void submit(ProfilePatch patch, Consumer<String> result);
    void uploadHosted(Path file, Consumer<String> success, Consumer<String> error);
    void uploadExternal(Path file, Consumer<String> success, Consumer<String> error);
    void execute(Runnable task);
    void feedback(boolean success);
}
