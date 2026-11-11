package com.atom.chat.onboarding;

import com.atom.chat.AtomChat;
import com.atom.chat.util.DataMigration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;

/** Non-distributable, durable prompt choice; deliberately not part of cache or config. */
public final class OnboardingStore {
    private final Path file;
    private static final java.util.Map<Path,String> SESSION_CHOICES = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean decided, damaged, failed;
    private String choice;

    public OnboardingStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
        try {
            DataMigration.noLinks(file);
            if (Files.exists(file)) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String choice = json.has("promptChoice") ? json.get("promptChoice").getAsString() : "";
                decided = json.has("schema") && json.get("schema").getAsInt() == 1
                        && (choice.equals("watch") || choice.equals("dismissed"));
                damaged = !decided;
            }
        } catch (IOException | RuntimeException e) {
            damaged = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("config", "Could not read tutorial prompt state at {}"), file, e);
        }
        String pending = SESSION_CHOICES.get(this.file);
        if (!decided && pending != null) { decided = true; choice = pending; persist(); }
    }

    public boolean needsPrompt() { return !decided; }
    public boolean saveFailed() { return failed; }
    /** Retry outside rendering, when the user reopens the panel or explicitly replays. */
    public void retry() { if (failed && choice != null) persist(); }

    public void choose(String choice) {
        if (!choice.equals("watch") && !choice.equals("dismissed")) throw new IllegalArgumentException(choice);
        if (decided) return;
        decided = true;
        this.choice = choice;
        SESSION_CHOICES.put(file, choice);
        persist();
    }
    private void persist() {
        try {
            DataMigration.noLinks(file);
            Files.createDirectories(file.getParent());
            if (damaged && Files.isRegularFile(file)) {
                Path backup = Files.createTempFile(file.getParent(), "onboarding-original-", ".json");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            JsonObject json = new JsonObject();
            json.addProperty("schema", 1);
            json.addProperty("promptChoice", choice);
            Path staged = Files.createTempFile(file.getParent(), "onboarding-", ".tmp");
            try {
                Files.writeString(staged, json.toString());
                if (!JsonParser.parseString(Files.readString(staged)).equals(json)) throw new IOException("Tutorial read-back failed");
                try { Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING); }
                failed = false;
                SESSION_CHOICES.remove(file);
            } finally { Files.deleteIfExists(staged); }
        } catch (IOException | RuntimeException e) {
            failed = true;
            AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("config", "Could not persist tutorial choice at {}; retry when panel reopens"), file, e);
        }
    }
}
