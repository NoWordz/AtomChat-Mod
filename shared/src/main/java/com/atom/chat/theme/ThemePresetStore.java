package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageBounds;
import com.atom.chat.wallpaper.WallpaperStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Named appearance presets with an optional bundled wallpaper.
 *
 * <p>Pure {@code java.nio} + Gson: no Minecraft and no Skia imports, so the
 * save/apply/overwrite/rename/delete rules are testable against a temporary
 * directory. The snapshot is an explicit field whitelist (see
 * {@link Snapshot}); the whole config is deliberately never serialised.</p>
 *
 * <p>One directory per preset: {@code <dir>/<uuid>/preset.json} plus, when the
 * preset carries one, a fixed {@code wallpaper.<ext>} beside it. The display
 * name never contributes to a path. Writes stage every file first and then
 * swap the preset directory into place, so a failed save leaves the previous
 * preset intact. {@link #list()} caches the scan and refreshes only after a
 * write, never per frame.</p>
 */
public final class ThemePresetStore {
    public static final int MAX_PRESETS = 32;
    public static final int MAX_NAME_CODE_POINTS = 48;

    private static final String MANIFEST = "preset.json";
    private static final String WALLPAPER_BASE = "wallpaper";
    private static final Pattern ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static ThemePresetStore shared;

    /** Explicit appearance snapshot; nothing outside this whitelist is stored. */
    public record Snapshot(int accentColor, int ownBubbleColor, int bubbleTextColor,
                           int otherBubbleColor, int otherBubbleTextColor,
                           int panelBgColor, int textPrimaryColor, int textSecondaryColor,
                           int cardColor, int panelOutlineColor,
                           int secondaryCapsuleBg, int secondaryCapsuleText,
                           float panelOpacity, boolean blurEnabled, boolean panelOutline,
                           float cardTint, float cornerRadius, float panelWidth,
                           float panelHeight, float uiScale, float contentScale,
                           boolean animationEnabled, String pageNavStyle, String themeName) {
        public static Snapshot capture(AtomChatConfig config) {
            return new Snapshot(
                    config.accentColor, config.ownBubbleColor, config.bubbleTextColor,
                    config.otherBubbleColor, config.otherBubbleTextColor,
                    config.panelBgColor, config.textPrimaryColor, config.textSecondaryColor,
                    config.cardColor, config.panelOutlineColor,
                    config.secondaryCapsuleBg, config.secondaryCapsuleText,
                    config.panelOpacity, config.blurEnabled, config.panelOutline,
                    config.cardTint, config.cornerRadius, config.panelWidth,
                    config.panelHeight, config.uiScale, config.contentScale,
                    config.animationEnabled, config.pageNavStyle.name(),
                    config.themeName == null ? "" : config.themeName);
        }

        public void applyTo(AtomChatConfig config) {
            config.accentColor = accentColor;
            config.ownBubbleColor = ownBubbleColor;
            config.bubbleTextColor = bubbleTextColor;
            config.otherBubbleColor = otherBubbleColor;
            config.otherBubbleTextColor = otherBubbleTextColor;
            config.panelBgColor = panelBgColor;
            config.textPrimaryColor = textPrimaryColor;
            config.textSecondaryColor = textSecondaryColor;
            config.cardColor = cardColor;
            config.panelOutlineColor = panelOutlineColor;
            config.secondaryCapsuleBg = secondaryCapsuleBg;
            config.secondaryCapsuleText = secondaryCapsuleText;
            config.panelOpacity = panelOpacity;
            config.blurEnabled = blurEnabled;
            config.panelOutline = panelOutline;
            config.cardTint = cardTint;
            config.cornerRadius = cornerRadius;
            config.panelWidth = panelWidth;
            config.panelHeight = panelHeight;
            config.uiScale = uiScale;
            config.contentScale = contentScale;
            config.animationEnabled = animationEnabled;
            config.pageNavStyle = AtomChatConfig.PageNavStyle.valueOf(pageNavStyle);
            config.themeName = themeName == null ? "" : themeName;
        }
    }

    public record Preset(String id, String name, boolean hasWallpaper,
                         String wallpaperExtension, Snapshot snapshot) {
    }

    public record Result(boolean ok, String error, Preset preset) {
        public static Result success(Preset preset) {
            return new Result(true, "", preset);
        }

        public static Result failure(String error) {
            return new Result(false, error, null);
        }
    }

    private final Path dir;
    private List<Preset> cache;

    public ThemePresetStore(Path dir) {
        this.dir = dir;
    }

    /** Installs the store used by the settings UI (client init). */
    public static synchronized ThemePresetStore init(Path dir) {
        shared = new ThemePresetStore(dir);
        return shared;
    }

    public static ThemePresetStore get() {
        ThemePresetStore store = shared;
        return store != null ? store : new ThemePresetStore(null);
    }

    public synchronized List<Preset> list() {
        if (dir == null) {
            return List.of();
        }
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    public synchronized Result save(String name, AtomChatConfig config, Path wallpaperSource) {
        String clean = validName(name);
        if (clean == null) {
            return Result.failure("invalid_name");
        }
        if (!validDir()) {
            return Result.failure("unavailable");
        }
        if (list().size() >= MAX_PRESETS) {
            return Result.failure("too_many");
        }
        return write(UUID.randomUUID().toString(), clean, Snapshot.capture(config), wallpaperSource, false);
    }

    public synchronized Result overwrite(String id, AtomChatConfig config, Path wallpaperSource) {
        if (!validId(id)) {
            return Result.failure("invalid_id");
        }
        Preset existing = find(id);
        if (existing == null) {
            return Result.failure("not_found");
        }
        return write(existing.id(), existing.name(), Snapshot.capture(config), wallpaperSource, true);
    }

    public synchronized Result rename(String id, String newName) {
        if (!validId(id)) {
            return Result.failure("invalid_id");
        }
        Preset existing = find(id);
        if (existing == null) {
            return Result.failure("not_found");
        }
        String clean = validName(newName);
        if (clean == null) {
            return Result.failure("invalid_name");
        }
        Path manifest = dir.resolve(existing.id()).resolve(MANIFEST);
        try {
            Path tmp = manifest.resolveSibling(MANIFEST + ".tmp");
            Files.writeString(tmp, toJson(existing.id(), clean, existing),
                    StandardCharsets.UTF_8);
            moveReplace(tmp, manifest);
            cache = null;
            return Result.success(new Preset(existing.id(), clean, existing.hasWallpaper(),
                    existing.wallpaperExtension(), existing.snapshot()));
        } catch (IOException e) {
            return Result.failure("io");
        }
    }

    public synchronized Result delete(String id) {
        if (!validId(id)) {
            return Result.failure("invalid_id");
        }
        Preset existing = find(id);
        if (existing == null) {
            return Result.failure("not_found");
        }
        Path target = dir.resolve(existing.id());
        try {
            deleteTree(target);
            cache = null;
            return Result.success(existing);
        } catch (IOException e) {
            cache = null;
            return Result.failure("io");
        }
    }

    public synchronized Result apply(String id, AtomChatConfig config) {
        if (!validId(id)) {
            return Result.failure("invalid_id");
        }
        Preset preset = dir == null ? null : read(dir.resolve(id), id);
        if (preset == null) {
            return Result.failure("not_found");
        }
        Path wallpaper = preset.hasWallpaper()
                ? dir.resolve(preset.id()).resolve(WALLPAPER_BASE + preset.wallpaperExtension())
                : null;
        if (wallpaper != null && !validWallpaper(wallpaper)) {
            return Result.failure("invalid_wallpaper");
        }
        Snapshot previous = Snapshot.capture(config);
        Path oldWallpaper = WallpaperStore.current();
        Path backup = null;
        try {
            if (oldWallpaper != null) {
                backup = Files.createTempFile("atomchat-wallpaper-", extensionOf(oldWallpaper.toString()));
                Files.copy(oldWallpaper, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            if (wallpaper != null ? !WallpaperStore.set(wallpaper) : !clearWallpaper()) {
                return Result.failure("wallpaper_io");
            }
            preset.snapshot().applyTo(config);
            if (!AtomChatConfig.saveChecked(config)) {
                previous.applyTo(config);
                if (backup == null) WallpaperStore.clear();
                else WallpaperStore.set(backup);
                return Result.failure("config_io");
            }
            return Result.success(preset);
        } catch (IOException e) {
            return Result.failure("wallpaper_io");
        } finally {
            if (backup != null) {
                try { Files.deleteIfExists(backup); } catch (IOException ignored) { }
            }
        }
    }

    private static boolean clearWallpaper() {
        WallpaperStore.clear();
        return !WallpaperStore.isSet();
    }

    private Result write(String id, String name, Snapshot snapshot, Path wallpaperSource,
                         boolean overwrite) {
        if (!validSnapshot(snapshot)) {
            return Result.failure("invalid_snapshot");
        }
        String extension = null;
        if (wallpaperSource != null) {
            if (!validWallpaper(wallpaperSource)) {
                return Result.failure("invalid_wallpaper");
            }
            extension = extensionOf(wallpaperSource.getFileName().toString());
        }
        if (!overwrite && find(id) != null) {
            return Result.failure("duplicate");
        }
        Path stage = null;
        Path backup = null;
        try {
            Files.createDirectories(dir);
            stage = dir.resolve(".stage-" + id + "-" + System.nanoTime());
            Files.createDirectories(stage);
            Files.writeString(stage.resolve(MANIFEST),
                    toJson(id, name, new Preset(id, name, extension != null, extension, snapshot)),
                    StandardCharsets.UTF_8);
            if (wallpaperSource != null) {
                Files.copy(wallpaperSource, stage.resolve(WALLPAPER_BASE + extension),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Path target = dir.resolve(id);
            if (Files.exists(target)) {
                backup = dir.resolve(".old-" + id + "-" + System.nanoTime());
                moveReplace(target, backup);
            }
            try {
                moveReplace(stage, target);
            } catch (IOException commitFailed) {
                if (backup != null) {
                    try {
                        moveReplace(backup, target);
                    } catch (IOException ignored) {
                        // Best effort: the old preset stays in its backup dir.
                    }
                }
                throw commitFailed;
            }
            cache = null;
            if (backup != null) {
                try {
                    deleteTree(backup);
                } catch (IOException ignored) {
                    // The new preset is committed; cleanup cannot undo success.
                }
            }
            return Result.success(new Preset(id, name, extension != null, extension, snapshot));
        } catch (IOException e) {
            if (stage != null) {
                try {
                    deleteTree(stage);
                } catch (IOException ignored) {
                    // Staging leftovers are hidden and harmless.
                }
            }
            return Result.failure("io");
        }
    }

    private List<Preset> load() {
        List<Preset> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                String id = entry.getFileName().toString();
                if (!ID.matcher(id).matches()) {
                    continue;
                }
                Preset preset = read(entry, id);
                if (preset != null) {
                    out.add(preset);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        out.sort(Comparator.comparing(Preset::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    private Preset read(Path presetDir, String id) {
        Path manifest = presetDir.resolve(MANIFEST);
        try {
            if (Files.size(manifest) > 65536) {
                return null;
            }
            JsonObject root = JsonParser.parseString(
                    Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!id.equals(root.get("id").getAsString())) {
                return null;
            }
            String name = validName(root.get("name").getAsString());
            if (name == null) {
                return null;
            }
            String extension = root.has("wallpaper") && !root.get("wallpaper").isJsonNull()
                    ? root.get("wallpaper").getAsString() : null;
            if (extension != null && !validExtension(extension)) {
                return null;
            }
            Snapshot snapshot = readSnapshot(root.getAsJsonObject("snapshot"));
            if (snapshot == null) {
                return null;
            }
            return new Preset(id, name, extension != null, extension, snapshot);
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    private Preset find(String id) {
        if (!validId(id)) {
            return null;
        }
        for (Preset preset : list()) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    private boolean validDir() {
        return dir != null;
    }

    private static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        return name.codePointCount(0, name.length()) <= MAX_NAME_CODE_POINTS ? name : null;
    }

    private static boolean validWallpaper(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > ImageBounds.MAX_BYTES) {
                return false;
            }
            ImageBounds.Inspection inspection = ImageBounds.inspect(file);
            return inspection.dimensions() != null && inspection.rejection() == null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".png" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean validExtension(String extension) {
        return ".png".equals(extension) || ".jpg".equals(extension)
                || ".jpeg".equals(extension) || ".webp".equals(extension);
    }

    private static Snapshot readSnapshot(JsonObject json) {
        if (json == null) {
            return null;
        }
        try {
            Snapshot snapshot = new Snapshot(
                    json.get("accentColor").getAsInt(),
                    json.get("ownBubbleColor").getAsInt(),
                    json.get("bubbleTextColor").getAsInt(),
                    json.get("otherBubbleColor").getAsInt(),
                    json.get("otherBubbleTextColor").getAsInt(),
                    json.get("panelBgColor").getAsInt(),
                    json.get("textPrimaryColor").getAsInt(),
                    json.get("textSecondaryColor").getAsInt(),
                    json.get("cardColor").getAsInt(),
                    json.get("panelOutlineColor").getAsInt(),
                    json.get("secondaryCapsuleBg").getAsInt(),
                    json.get("secondaryCapsuleText").getAsInt(),
                    json.get("panelOpacity").getAsFloat(),
                    json.get("blurEnabled").getAsBoolean(),
                    json.get("panelOutline").getAsBoolean(),
                    json.get("cardTint").getAsFloat(),
                    json.get("cornerRadius").getAsFloat(),
                    json.get("panelWidth").getAsFloat(),
                    json.get("panelHeight").getAsFloat(),
                    json.get("uiScale").getAsFloat(),
                    json.get("contentScale").getAsFloat(),
                    json.get("animationEnabled").getAsBoolean(),
                    json.get("pageNavStyle").getAsString(),
                    json.has("themeName") ? json.get("themeName").getAsString() : "");
            return validSnapshot(snapshot) ? snapshot : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean validSnapshot(Snapshot s) {
        if (s == null) {
            return false;
        }
        try {
            AtomChatConfig.PageNavStyle.valueOf(s.pageNavStyle());
        } catch (RuntimeException e) {
            return false;
        }
        return finite(s.panelOpacity(), 0.0F, 1.0F)
                && finite(s.cardTint(), 0.0F, 1.0F)
                && finite(s.cornerRadius(), 0.0F, 25.0F)
                && finite(s.panelWidth(), 100.0F, 2000.0F)
                && finite(s.panelHeight(), 100.0F, 4000.0F)
                && finite(s.uiScale(), 0.5F, 1.5F)
                && finite(s.contentScale(), 0.8F, 1.5F);
    }

    private static boolean finite(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    private static String toJson(String id, String name, Preset preset) {
        Snapshot snapshot = preset.snapshot();
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("name", name);
        root.addProperty("wallpaper", preset.wallpaperExtension());
        JsonObject json = new JsonObject();
        json.addProperty("accentColor", snapshot.accentColor());
        json.addProperty("ownBubbleColor", snapshot.ownBubbleColor());
        json.addProperty("bubbleTextColor", snapshot.bubbleTextColor());
        json.addProperty("otherBubbleColor", snapshot.otherBubbleColor());
        json.addProperty("otherBubbleTextColor", snapshot.otherBubbleTextColor());
        json.addProperty("panelBgColor", snapshot.panelBgColor());
        json.addProperty("textPrimaryColor", snapshot.textPrimaryColor());
        json.addProperty("textSecondaryColor", snapshot.textSecondaryColor());
        json.addProperty("cardColor", snapshot.cardColor());
        json.addProperty("panelOutlineColor", snapshot.panelOutlineColor());
        json.addProperty("secondaryCapsuleBg", snapshot.secondaryCapsuleBg());
        json.addProperty("secondaryCapsuleText", snapshot.secondaryCapsuleText());
        json.addProperty("panelOpacity", snapshot.panelOpacity());
        json.addProperty("blurEnabled", snapshot.blurEnabled());
        json.addProperty("panelOutline", snapshot.panelOutline());
        json.addProperty("cardTint", snapshot.cardTint());
        json.addProperty("cornerRadius", snapshot.cornerRadius());
        json.addProperty("panelWidth", snapshot.panelWidth());
        json.addProperty("panelHeight", snapshot.panelHeight());
        json.addProperty("uiScale", snapshot.uiScale());
        json.addProperty("contentScale", snapshot.contentScale());
        json.addProperty("animationEnabled", snapshot.animationEnabled());
        json.addProperty("pageNavStyle", snapshot.pageNavStyle());
        json.addProperty("themeName", snapshot.themeName());
        root.add("snapshot", json);
        return root.toString();
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
