package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageBounds;
import com.atom.chat.wallpaper.WallpaperStore;
import com.google.gson.JsonElement;
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
 * Automatically numbered appearance definitions owned by the client.
 *
 * <p>The manifest is an explicit whitelist: twelve colours plus the layout,
 * opacity, card, corner, motion and page-navigation values. Chat, privacy,
 * audio, identity and history are never serialised. A preset owns a separate
 * wallpaper copy; applying a preset with no wallpaper clears the active one.</p>
 *
 * <p>Each directory has a UUID identity unrelated to its display number. The
 * smallest free positive number is assigned on save, and deleting a preset
 * never renumbers its neighbours. Listing validates every manifest on its
 * first scan and ignores damaged entries. Applying re-reads and validates the
 * selected manifest before touching live state.</p>
 */
public final class AppearancePresetStore {
    public static final int MAX_PRESETS = 32;
    public static final long MAX_WALLPAPER_BYTES = 16L * 1024L * 1024L;

    private static final int SCHEMA = 1;
    private static final String MANIFEST = "preset.json";
    private static final String WALLPAPER = "wallpaper";
    private static final Pattern ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static AppearancePresetStore shared;

    public record Snapshot(int accentColor, int ownBubbleColor, int bubbleTextColor,
                           int otherBubbleColor, int otherBubbleTextColor,
                           int panelBgColor, int textPrimaryColor, int textSecondaryColor,
                           int cardColor, int panelOutlineColor,
                           int secondaryCapsuleBg, int secondaryCapsuleText,
                           float panelOpacity, float panelWidth, float panelHeight,
                           float uiScale, float contentScale, float cardTint,
                           float cornerRadius, boolean blurEnabled, boolean panelOutline,
                           boolean animationEnabled, String pageNavStyle, String themeName) {
        public static Snapshot capture(AtomChatConfig config) {
            return new Snapshot(config.accentColor, config.ownBubbleColor, config.bubbleTextColor,
                    config.otherBubbleColor, config.otherBubbleTextColor,
                    config.panelBgColor, config.textPrimaryColor, config.textSecondaryColor,
                    config.cardColor, config.panelOutlineColor,
                    config.secondaryCapsuleBg, config.secondaryCapsuleText,
                    config.panelOpacity, config.panelWidth, config.panelHeight,
                    config.uiScale, config.contentScale, config.cardTint, config.cornerRadius,
                    config.blurEnabled, config.panelOutline, config.animationEnabled,
                    config.pageNavStyle.name(), config.themeName == null ? "" : config.themeName);
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
            config.panelWidth = panelWidth;
            config.panelHeight = panelHeight;
            config.uiScale = uiScale;
            config.contentScale = contentScale;
            config.cardTint = cardTint;
            config.cornerRadius = cornerRadius;
            config.blurEnabled = blurEnabled;
            config.panelOutline = panelOutline;
            config.animationEnabled = animationEnabled;
            config.pageNavStyle = AtomChatConfig.PageNavStyle.valueOf(pageNavStyle);
            config.themeName = themeName == null ? "" : themeName;
        }
    }

    public record Preset(String id, int order, int number, boolean hasWallpaper,
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

    public AppearancePresetStore(Path dir) {
        this.dir = dir;
    }

    public static synchronized AppearancePresetStore init(Path dir) {
        shared = new AppearancePresetStore(dir);
        return shared;
    }

    public static AppearancePresetStore get() {
        AppearancePresetStore store = shared;
        return store != null ? store : new AppearancePresetStore(null);
    }

    public synchronized List<Preset> list() {
        if (dir == null) {
            return List.of();
        }
        if (cache == null) {
            cache = loadAll();
        }
        return cache;
    }

    /** Captures the live appearance under the smallest free positive number. */
    public synchronized Result saveCurrent(AtomChatConfig config, Path wallpaperSource) {
        if (dir == null) {
            return Result.failure("unavailable");
        }
        List<Preset> current = list();
        if (current.size() >= MAX_PRESETS) {
            return Result.failure("too_many");
        }
        Snapshot snapshot = Snapshot.capture(config);
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
        boolean[] used = new boolean[Math.max(MAX_PRESETS + 2, current.size() + 2)];
        for (Preset preset : current) {
            if (preset.number() > 0 && preset.number() < used.length) {
                used[preset.number()] = true;
            }
        }
        int number = 1;
        while (number < used.length && used[number]) {
            number++;
        }
        if (number >= used.length) {
            return Result.failure("too_many");
        }
        int order = current.stream().mapToInt(Preset::order).max().orElse(0) + 1;
        String id = UUID.randomUUID().toString();
        return write(id, order, number, snapshot, wallpaperSource, extension);
    }

    public synchronized Result apply(String id, AtomChatConfig config) {
        if (!validId(id) || dir == null) {
            return Result.failure("invalid_id");
        }
        Preset preset = read(dir.resolve(id), id);
        if (preset == null) {
            return Result.failure("invalid_preset");
        }
        Path wallpaper = preset.hasWallpaper()
                ? dir.resolve(id).resolve(WALLPAPER + preset.wallpaperExtension()) : null;
        if (wallpaper != null && !validWallpaper(wallpaper)) {
            return Result.failure("invalid_wallpaper");
        }

        Snapshot previous = Snapshot.capture(config);
        Path activeWallpaper = WallpaperStore.current();
        Path backup = null;
        boolean backupReady = false;
        boolean wallpaperTouched = false;
        try {
            if (activeWallpaper != null) {
                Path backupDir = dir.resolve(".wallpaper-backup");
                Files.createDirectories(backupDir);
                backup = Files.createTempFile(backupDir, "wallpaper-",
                        extensionOf(activeWallpaper.toString()));
                Files.copy(activeWallpaper, backup, StandardCopyOption.REPLACE_EXISTING);
                backupReady = true;
            }
            wallpaperTouched = true;
            if (wallpaper != null ? !WallpaperStore.set(wallpaper) : !clearWallpaper()) {
                restoreWallpaper(backup, backupReady, activeWallpaper != null);
                return Result.failure("wallpaper_io");
            }
            preset.snapshot().applyTo(config);
            if (!AtomChatConfig.saveChecked(config)) {
                previous.applyTo(config);
                restoreWallpaper(backup, backupReady, activeWallpaper != null);
                return Result.failure("config_io");
            }
            return Result.success(preset);
        } catch (IOException | RuntimeException e) {
            previous.applyTo(config);
            if (wallpaperTouched) {
                restoreWallpaper(backup, backupReady, activeWallpaper != null);
            }
            return Result.failure("apply_io");
        } finally {
            if (backup != null) {
                try { Files.deleteIfExists(backup); } catch (IOException ignored) { }
            }
        }
    }

    public synchronized Result delete(String id) {
        if (!validId(id) || dir == null) {
            return Result.failure("invalid_id");
        }
        Preset existing = read(dir.resolve(id), id);
        if (existing == null) {
            return Result.failure("not_found");
        }
        try {
            deleteTree(dir.resolve(id));
            cache = null;
            return Result.success(existing);
        } catch (IOException e) {
            cache = null;
            return Result.failure("io");
        }
    }

    private Result write(String id, int order, int number, Snapshot snapshot,
                         Path wallpaperSource, String extension) {
        Path stage = dir.resolve(".stage-" + id + "-" + UUID.randomUUID());
        Path target = dir.resolve(id);
        Path backup = dir.resolve(".backup-" + id + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(stage);
            Files.writeString(stage.resolve(MANIFEST),
                    toJson(id, order, number, snapshot, extension), StandardCharsets.UTF_8);
            if (wallpaperSource != null) {
                Files.copy(wallpaperSource, stage.resolve(WALLPAPER + extension),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(target)) {
                move(target, backup);
            }
            try {
                move(stage, target);
            } catch (IOException e) {
                if (Files.exists(backup) && !Files.exists(target)) {
                    move(backup, target);
                }
                throw e;
            }
            deleteTree(backup);
            cache = null;
            Preset preset = new Preset(id, order, number, extension != null, extension, snapshot);
            return Result.success(preset);
        } catch (IOException e) {
            deleteTreeQuietly(stage);
            cache = null;
            return Result.failure("io");
        }
    }

    private List<Preset> loadAll() {
        List<Preset> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> validId(p.getFileName().toString()))
                    .forEach(path -> {
                        Preset preset = read(path, path.getFileName().toString());
                        if (preset != null) {
                            out.add(preset);
                        }
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        out.sort(Comparator.comparingInt(Preset::order)
                .thenComparingInt(Preset::number).thenComparing(Preset::id));
        return out;
    }

    private static Preset read(Path presetDir, String id) {
        Path manifest = presetDir.resolve(MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (integer(root, "schema") != SCHEMA
                    || !id.equals(string(root, "id"))) {
                return null;
            }
            int number = integer(root, "number");
            if (number <= 0 || number > MAX_PRESETS) {
                return null;
            }
            int order = integer(root, "order");
            if (order <= 0) {
                return null;
            }
            JsonObject a = object(root, "appearance");
            Snapshot snapshot = new Snapshot(
                    color(a, "accentColor"), color(a, "ownBubbleColor"),
                    color(a, "bubbleTextColor"), color(a, "otherBubbleColor"),
                    color(a, "otherBubbleTextColor"), color(a, "panelBgColor"),
                    color(a, "textPrimaryColor"), color(a, "textSecondaryColor"),
                    color(a, "cardColor"), color(a, "panelOutlineColor"),
                    color(a, "secondaryCapsuleBg"), color(a, "secondaryCapsuleText"),
                    number(a, "panelOpacity"), number(a, "panelWidth"), number(a, "panelHeight"),
                    number(a, "uiScale"), number(a, "contentScale"), number(a, "cardTint"),
                    number(a, "cornerRadius"), bool(a, "blurEnabled"), bool(a, "panelOutline"),
                    bool(a, "animationEnabled"), string(a, "pageNavStyle"),
                    string(a, "themeName"));
            if (!validSnapshot(snapshot)) {
                return null;
            }
            String extension = root.has("wallpaper") && !root.get("wallpaper").isJsonNull()
                    ? string(root, "wallpaper") : null;
            if (extension != null) {
                extension = extension.toLowerCase(Locale.ROOT);
                if (!supportedExtension(extension)
                        || !validWallpaper(presetDir.resolve(WALLPAPER + extension))) {
                    return null;
                }
            }
            return new Preset(id, order, number, extension != null, extension, snapshot);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean validSnapshot(Snapshot s) {
        if (s == null) return false;
        if (!finite(s.panelOpacity(), 0F, 1F)
                || !finite(s.panelWidth(), 400F, 600F)
                || !finite(s.panelHeight(), 600F, 1200F)
                || !finite(s.uiScale(), 0.75F, 1.5F)
                || !finite(s.contentScale(), 0.8F, 1.5F)
                || !finite(s.cardTint(), 0F, 1F)
                || !finite(s.cornerRadius(), 0F, 25F)) {
            return false;
        }
        try {
            AtomChatConfig.PageNavStyle.valueOf(s.pageNavStyle());
        } catch (RuntimeException e) {
            return false;
        }
        return s.themeName() != null && s.themeName().length() <= 128;
    }

    private static boolean finite(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    private static boolean validWallpaper(Path path) {
        try {
            return path != null && Files.isRegularFile(path)
                    && Files.size(path) > 0 && Files.size(path) <= MAX_WALLPAPER_BYTES
                    && supportedExtension(extensionOf(path.getFileName().toString()))
                    && ImageBounds.inspect(path).rejection() == null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean supportedExtension(String extension) {
        return ".png".equals(extension) || ".jpg".equals(extension)
                || ".jpeg".equals(extension) || ".webp".equals(extension);
    }

    private static boolean clearWallpaper() {
        WallpaperStore.clear();
        return !WallpaperStore.isSet();
    }

    private static void restoreWallpaper(Path backup, boolean backupReady,
                                         boolean originalHadWallpaper) {
        if (!originalHadWallpaper) {
            WallpaperStore.clear();
        } else if (backupReady && backup != null) {
            WallpaperStore.set(backup);
        }
    }

    private static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key);
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException(key);
        return value.getAsInt();
    }

    private static int color(JsonObject object, String key) {
        return integer(object, key);
    }

    private static float number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException(key);
        return value.getAsFloat();
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key);
        return value.getAsBoolean();
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key);
        return value.getAsJsonObject();
    }

    private static String toJson(String id, int order, int number, Snapshot s, String extension) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("id", id);
        root.addProperty("order", order);
        root.addProperty("number", number);
        if (extension == null) root.add("wallpaper", null); else root.addProperty("wallpaper", extension);
        JsonObject a = new JsonObject();
        a.addProperty("accentColor", s.accentColor());
        a.addProperty("ownBubbleColor", s.ownBubbleColor());
        a.addProperty("bubbleTextColor", s.bubbleTextColor());
        a.addProperty("otherBubbleColor", s.otherBubbleColor());
        a.addProperty("otherBubbleTextColor", s.otherBubbleTextColor());
        a.addProperty("panelBgColor", s.panelBgColor());
        a.addProperty("textPrimaryColor", s.textPrimaryColor());
        a.addProperty("textSecondaryColor", s.textSecondaryColor());
        a.addProperty("cardColor", s.cardColor());
        a.addProperty("panelOutlineColor", s.panelOutlineColor());
        a.addProperty("secondaryCapsuleBg", s.secondaryCapsuleBg());
        a.addProperty("secondaryCapsuleText", s.secondaryCapsuleText());
        a.addProperty("panelOpacity", s.panelOpacity());
        a.addProperty("panelWidth", s.panelWidth());
        a.addProperty("panelHeight", s.panelHeight());
        a.addProperty("uiScale", s.uiScale());
        a.addProperty("contentScale", s.contentScale());
        a.addProperty("cardTint", s.cardTint());
        a.addProperty("cornerRadius", s.cornerRadius());
        a.addProperty("blurEnabled", s.blurEnabled());
        a.addProperty("panelOutline", s.panelOutline());
        a.addProperty("animationEnabled", s.animationEnabled());
        a.addProperty("pageNavStyle", s.pageNavStyle());
        a.addProperty("themeName", s.themeName());
        root.add("appearance", a);
        return root.toString();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void deleteTreeQuietly(Path root) {
        try { deleteTree(root); } catch (IOException ignored) { }
    }
}
