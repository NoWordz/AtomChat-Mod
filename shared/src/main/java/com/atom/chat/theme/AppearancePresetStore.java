package com.atom.chat.theme;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageBounds;
import com.atom.chat.util.DataMigration;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Automatically numbered appearance definitions owned by the client.
 *
 * <p>The manifest is an explicit whitelist: twelve colours plus the layout,
 * opacity, card, corner, motion and page-navigation values. Chat, privacy,
 * audio, identity and history are never serialised. New presets own their
 * static image or record a dynamic wallpaper ID or an empty background.</p>
 *
 * <p>Each directory has a UUID identity unrelated to its display number. The
 * smallest free positive number is assigned on save, and deleting a preset
 * never renumbers its neighbours. Listing validates every manifest on its
 * first scan and ignores damaged entries. Applying re-reads and validates the
 * selected manifest before touching live state.</p>
 */
public final class AppearancePresetStore {
    public static final int MAX_PRESETS = 32;
    public static final long MAX_WALLPAPER_BYTES = ImageBounds.MAX_BYTES;

    private static final int LEGACY_SCHEMA = 1;
    private static final int SCHEMA = 2;
    private static final String MANIFEST = "preset.json";
    private static final String WALLPAPER = "wallpaper";
    private static final Pattern ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static volatile AppearancePresetStore shared;

    private enum ApplyMode { SNAPSHOT, LEGACY_PRESET, PRESET, RESTORE }

    public record Snapshot(int accentColor, int ownBubbleColor, int bubbleTextColor,
                           int otherBubbleColor, int otherBubbleTextColor,
                           int panelBgColor, int textPrimaryColor, int textSecondaryColor,
                           int cardColor, int panelOutlineColor,
                           int secondaryCapsuleBg, int secondaryCapsuleText,
                           float panelOpacity, float panelWidth, float panelHeight, boolean panelWidthAutomatic, boolean panelHeightAutomatic,
                           float uiScale, float contentScale, float cardTint, float cardBlurStrength,
                           float cornerRadius, boolean blurEnabled, boolean panelOutline, float panelOutlineWidth,
                           boolean animationEnabled, String pageNavStyle, String themeName,
                           String bubbleStyle, Float panelBlurStrength) {
        public static Snapshot capture(AtomChatConfig config) {
            return capture(config, null, false);
        }

        private static Snapshot capture(AtomChatConfig config, Snapshot expected, boolean raw) {
            boolean keepWidth = expected != null && config.halfScreenMode;
            float cardBlur = raw ? config.cardBlurStrength : config.storedCardBlurStrength();
            Float panelBlur = raw ? config.blurStrength : config.storedBlurStrength();
            if (expected != null && expected.panelBlurStrength == null) panelBlur = null;
            return new Snapshot(config.accentColor, config.ownBubbleColor, config.bubbleTextColor,
                    config.otherBubbleColor, config.otherBubbleTextColor,
                    config.panelBgColor, config.textPrimaryColor, config.textSecondaryColor,
                    config.cardColor, config.panelOutlineColor,
                    config.secondaryCapsuleBg, config.secondaryCapsuleText,
                    config.panelOpacity, keepWidth ? expected.panelWidth : config.panelWidth, config.panelHeight,
                    keepWidth ? expected.panelWidthAutomatic : config.panelWidthAutomatic, config.panelHeightAutomatic,
                    config.uiScale, config.contentScale, config.cardTint, cardBlur,
                    config.cornerRadius, config.blurEnabled,
                    config.panelOutline, raw ? config.panelOutlineWidth : config.effectivePanelOutlineWidth(),
                    config.animationEnabled, enumName(config.pageNavStyle),
                    raw || config.themeName != null ? config.themeName : "",
                    enumName(raw ? config.bubbleStyle : config.effectiveBubbleStyle()), panelBlur);
        }

        private static String enumName(Enum<?> value) {
            return value == null ? null : value.name();
        }

        public void applyTo(AtomChatConfig config) {
            copyTo(config, ApplyMode.SNAPSHOT);
        }

        private void copyTo(AtomChatConfig config, ApplyMode mode) {
            boolean restore = mode == ApplyMode.RESTORE;
            boolean keepEffects = (mode == ApplyMode.SNAPSHOT || mode == ApplyMode.LEGACY_PRESET)
                    && config.performanceModeEnabled();
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
            if (restore || !config.halfScreenMode) {
                config.panelWidth = panelWidth;
                config.panelWidthAutomatic = panelWidthAutomatic;
            }
            config.panelHeight = panelHeight;
            config.panelHeightAutomatic = panelHeightAutomatic;
            config.uiScale = uiScale;
            config.contentScale = contentScale;
            config.cardTint = cardTint;
            config.cornerRadius = cornerRadius;
            if (restore) {
                config.panelOutline = panelOutline;
                config.panelOutlineWidth = panelOutlineWidth;
            } else {
                config.setPanelOutlineWidth(panelOutlineWidth);
            }
            if (!keepEffects) {
                config.cardBlurStrength = cardBlurStrength;
                config.blurEnabled = blurEnabled;
                config.animationEnabled = animationEnabled;
                config.pageNavStyle = pageNavStyle == null ? null : AtomChatConfig.PageNavStyle.valueOf(pageNavStyle);
            }
            if (panelBlurStrength != null && mode != ApplyMode.SNAPSHOT) {
                config.blurStrength = panelBlurStrength;
            }
            config.themeName = themeName;
            config.bubbleStyle = bubbleStyle == null ? null : AtomChatConfig.BubbleStyle.valueOf(bubbleStyle);
        }
    }

    /** A missing manifest key preserves the live value. An explicit null selects the built-in part. */
    private record Choice(boolean specified, String id) {
        private static Choice of(String id) { return new Choice(true, id); }

        private static Choice read(JsonObject appearance, String key) {
            if (!appearance.has(key)) return new Choice(false, null);
            return of(appearance.get(key).isJsonNull() ? null : string(appearance, key));
        }

        private boolean valid() { return id == null || AppearanceUi.validId(id); }
        private boolean matches(String current) { return !specified || Objects.equals(id, current); }

        private void write(JsonObject appearance, String key) {
            appearance.addProperty(key, id);
        }
    }

    private record Parts(Choice bubble, Choice outline, Choice avatar, Choice dynamic) {
        private static Parts capture(AtomChatConfig config) {
            return new Parts(Choice.of(config.bubbleAppearanceId), Choice.of(config.panelOutlineAppearanceId),
                    Choice.of(config.avatarFrameAppearanceId), Choice.of(config.dynamicWallpaperAppearanceId));
        }

        private static Parts read(JsonObject appearance) {
            return new Parts(Choice.read(appearance, "bubbleAppearanceId"),
                    Choice.read(appearance, "panelOutlineAppearanceId"), Choice.read(appearance, "avatarFrameAppearanceId"),
                    Choice.read(appearance, "dynamicWallpaperAppearanceId"));
        }

        private boolean valid() { return bubble.valid() && outline.valid() && avatar.valid() && dynamic.valid(); }

        private boolean matches(AtomChatConfig config) {
            return bubble.matches(config.bubbleAppearanceId) && outline.matches(config.panelOutlineAppearanceId)
                    && avatar.matches(config.avatarFrameAppearanceId) && dynamic.matches(config.dynamicWallpaperAppearanceId);
        }

        private void applyTo(AtomChatConfig config) {
            if (bubble.specified()) config.bubbleAppearanceId = bubble.id();
            if (outline.specified()) config.panelOutlineAppearanceId = outline.id();
            if (avatar.specified()) config.avatarFrameAppearanceId = avatar.id();
            if (dynamic.specified()) config.dynamicWallpaperAppearanceId = dynamic.id();
        }

        private void write(JsonObject appearance) {
            bubble.write(appearance, "bubbleAppearanceId");
            outline.write(appearance, "panelOutlineAppearanceId");
            avatar.write(appearance, "avatarFrameAppearanceId");
            dynamic.write(appearance, "dynamicWallpaperAppearanceId");
        }
    }

    private record WallpaperChoice(boolean specified, String extension, String identity) {}

    public record Preset(String id, int order, int number, Snapshot snapshot, Parts parts,
                         WallpaperChoice wallpaper, boolean legacy) {
        public boolean hasWallpaper() { return wallpaper.extension() != null || parts.dynamic().id() != null; }
        public String wallpaperExtension() { return wallpaper.extension(); }
        public Float panelBlurStrength() { return snapshot.panelBlurStrength(); }

        private boolean matches(AtomChatConfig config, Snapshot live) {
            if (!parts.matches(config) || (wallpaper.specified() && !WallpaperStore.matchesCurrent(wallpaper.identity()))) {
                return false;
            }
            Snapshot actual = config.halfScreenMode || snapshot.panelBlurStrength() == null
                    ? Snapshot.capture(config, snapshot, false) : live;
            return snapshot.equals(actual);
        }
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
        shared.refresh();
        return shared;
    }

    public static AppearancePresetStore get() {
        AppearancePresetStore store = shared;
        return store != null ? store : new AppearancePresetStore(null);
    }

    public synchronized List<Preset> list() {
        return cache == null ? refresh() : cache;
    }

    /** Disk validation belongs to refresh/write boundaries, never a warm paint. */
    public synchronized List<Preset> refresh() {
        cache = DataMigration.allowPersonalPath(dir) ? List.copyOf(loadAll()) : List.of();
        return cache;
    }

    /** Explicit theme choices take priority. Older configs inferred a preset from its values. */
    public static Preset selected(List<Preset> presets, AtomChatConfig config) {
        String selectedId = config.selectedAppearancePresetId;
        if (presets.isEmpty() || "".equals(selectedId)) return null;
        if (selectedId != null) {
            for (Preset preset : presets) {
                if (selectedId.equals(preset.id())) {
                    return preset.legacy() || preset.matches(config, Snapshot.capture(config)) ? preset : null;
                }
            }
            return null;
        }
        Snapshot live = Snapshot.capture(config);
        for (Preset preset : presets) {
            if (preset.matches(config, live)) return preset;
        }
        return null;
    }

    /** Captures the live appearance under the smallest free positive number. */
    public synchronized Result saveCurrent(AtomChatConfig config, Path wallpaperSource) {
        if (!DataMigration.allowPersonalPath(dir)) {
            return Result.failure("unavailable");
        }
        List<Preset> current = list();
        if (current.size() >= MAX_PRESETS) {
            return Result.failure("too_many");
        }
        Snapshot snapshot = Snapshot.capture(config);
        Parts parts = Parts.capture(config);
        if (!validSnapshot(snapshot) || !parts.valid()) {
            return Result.failure("invalid_snapshot");
        }
        if (parts.dynamic().id() != null) wallpaperSource = null;
        String extension = wallpaperSource == null ? null : extensionOf(wallpaperSource);
        if (extension != null && !WallpaperStore.isSupportedName(extension)) return Result.failure("invalid_wallpaper");
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
        return write(id, order, number, snapshot, parts, wallpaperSource, extension);
    }

    public synchronized Result apply(String id, AtomChatConfig config) {
        if (!validId(id) || dir == null || !DataMigration.allowPersonalPath(dir.resolve(id))) {
            return Result.failure("invalid_id");
        }
        Preset preset = read(dir.resolve(id), id);
        if (preset == null) {
            return Result.failure("invalid_preset");
        }
        boolean[] configAttempted = {false};
        BooleanSupplier commit = () -> {
            configAttempted[0] = true;
            return commitPreset(preset, config);
        };
        try {
            boolean saved;
            if (preset.wallpaper().specified()) {
                String extension = preset.wallpaperExtension();
                Path source = extension == null ? null : dir.resolve(id).resolve(WALLPAPER + extension);
                saved = WallpaperStore.replaceWithConfig(source, commit);
            } else {
                synchronized (WallpaperStore.class) {
                    saved = commit.getAsBoolean();
                }
            }
            if (!saved) return Result.failure(configAttempted[0] ? "config_io" : "wallpaper_io");
            if (cache != null) {
                cache = cache.stream().map(entry -> id.equals(entry.id()) ? preset : entry).toList();
            }
            return Result.success(preset);
        } catch (RuntimeException failure) {
            return Result.failure("apply_io");
        }
    }

    private static boolean commitPreset(Preset preset, AtomChatConfig config) {
        Snapshot previous = Snapshot.capture(config, null, true);
        Parts previousParts = Parts.capture(config);
        String previousSelection = config.selectedAppearancePresetId;
        boolean saved = false;
        try {
            preset.snapshot().copyTo(config, preset.legacy() ? ApplyMode.LEGACY_PRESET : ApplyMode.PRESET);
            preset.parts().applyTo(config);
            config.selectedAppearancePresetId = preset.id();
            saved = AtomChatConfig.saveChecked(config);
            return saved;
        } finally {
            if (!saved) {
                previous.copyTo(config, ApplyMode.RESTORE);
                previousParts.applyTo(config);
                config.selectedAppearancePresetId = previousSelection;
            }
        }
    }

    public synchronized Result delete(String id) {
        if (!validId(id) || dir == null || !DataMigration.allowPersonalPath(dir.resolve(id))) {
            return Result.failure("invalid_id");
        }
        Preset existing = read(dir.resolve(id), id);
        if (existing == null) {
            return Result.failure("not_found");
        }
        try {
            deleteTree(dir.resolve(id));
            refresh();
            return Result.success(existing);
        } catch (IOException e) {
            refresh();
            return Result.failure("io");
        }
    }

    private Result write(String id, int order, int number, Snapshot snapshot, Parts parts,
                         Path wallpaperSource, String extension) {
        Path stage = null;
        try {
            Path target = dir.resolve(id);
            DataMigration.noLinks(target);
            Files.createDirectories(dir);
            stage = Files.createTempDirectory(dir, ".stage-");
            String identity = wallpaperSource == null ? null
                    : WallpaperStore.copyValidated(wallpaperSource, stage.resolve(WALLPAPER + extension));
            Files.writeString(stage.resolve(MANIFEST),
                    toJson(id, order, number, snapshot, parts, extension), StandardCharsets.UTF_8);
            move(stage, target);
            Preset preset = new Preset(id, order, number, snapshot, parts,
                    new WallpaperChoice(true, extension, identity), false);
            var updated = new ArrayList<>(list());
            updated.add(preset);
            cache = List.copyOf(updated);
            return Result.success(preset);
        } catch (IOException failure) {
            deleteTreeQuietly(stage);
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
        if (!DataMigration.allowPersonalPath(manifest)) return null;
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            int schema = integer(root, "schema");
            if ((schema != LEGACY_SCHEMA && schema != SCHEMA) || !id.equals(string(root, "id"))) {
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
            Float panelBlurStrength = a.has("blurStrength") ? number(a, "blurStrength") : null;
            Snapshot snapshot = new Snapshot(
                    color(a, "accentColor"), color(a, "ownBubbleColor"),
                    color(a, "bubbleTextColor"), color(a, "otherBubbleColor"),
                    color(a, "otherBubbleTextColor"), color(a, "panelBgColor"),
                    color(a, "textPrimaryColor"), color(a, "textSecondaryColor"),
                    color(a, "cardColor"), color(a, "panelOutlineColor"),
                    color(a, "secondaryCapsuleBg"), color(a, "secondaryCapsuleText"),
                    number(a, "panelOpacity"), number(a, "panelWidth"), number(a, "panelHeight"),
                    a.has("panelWidthAutomatic") && bool(a, "panelWidthAutomatic"),
                    a.has("panelHeightAutomatic") && bool(a, "panelHeightAutomatic"),
                    number(a, "uiScale"), number(a, "contentScale"), number(a, "cardTint"),
                    a.has("cardBlurStrength") ? number(a, "cardBlurStrength") : 0F,
                    number(a, "cornerRadius"), bool(a, "blurEnabled"), bool(a, "panelOutline"),
                    a.has("panelOutlineWidth") ? number(a, "panelOutlineWidth") : (bool(a, "panelOutline") ? 2F : 0F),
                    bool(a, "animationEnabled"), string(a, "pageNavStyle"),
                    string(a, "themeName"),
                    a.has("bubbleStyle") ? string(a, "bubbleStyle") : "CLASSIC", panelBlurStrength);
            Parts parts = Parts.read(a);
            if (!validSnapshot(snapshot) || !parts.valid()) return null;

            boolean legacy = schema == LEGACY_SCHEMA;
            if (!legacy && root.has("wallpaper") && !parts.dynamic().specified()) return null;
            String extension = root.has("wallpaper") && !root.get("wallpaper").isJsonNull()
                    ? string(root, "wallpaper").toLowerCase(Locale.ROOT) : null;
            String identity = null;
            if (extension != null) {
                if (!WallpaperStore.isSupportedName(extension) || extension.charAt(0) != '.'
                        || extension.indexOf('.', 1) != -1) return null;
                if (!legacy) {
                    if (parts.dynamic().id() != null) return null;
                    Path image = presetDir.resolve(WALLPAPER + extension);
                    DataMigration.noLinks(image);
                    identity = WallpaperStore.validatedIdentity(image);
                }
            }
            // Schema 1 wrote null by default and treated historical static copies as independent.
            boolean replaceWallpaper = !legacy && (root.has("wallpaper") || parts.dynamic().id() != null);
            return new Preset(id, order, number, snapshot, parts,
                    new WallpaperChoice(replaceWallpaper, extension, identity), legacy);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean validSnapshot(Snapshot s) {
        if (s == null) return false;
        if (!finite(s.panelOpacity(), 0F, 1F)
                || !finite(s.panelWidth(), 1F, 16384F)
                || !finite(s.panelHeight(), 1F, 16384F)
                || !finite(s.uiScale(), 0.75F, 1.5F)
                || !finite(s.contentScale(), 0.8F, 1.5F)
                || !finite(s.cardTint(), 0F, 1F)
                || !finite(s.cardBlurStrength(), 0F, 3F)
                || !finite(s.cornerRadius(), 0F, AtomChatConfig.MAX_CORNER_RADIUS)
                || !finite(s.panelOutlineWidth(), 0F, 6F)
                || (s.panelBlurStrength() != null && !finite(s.panelBlurStrength(), 0.25F, 3F))) {
            return false;
        }
        try {
            AtomChatConfig.PageNavStyle.valueOf(s.pageNavStyle());
            AtomChatConfig.BubbleStyle.valueOf(s.bubbleStyle());
        } catch (RuntimeException e) {
            return false;
        }
        return s.themeName() != null && s.themeName().length() <= 128;
    }

    private static boolean finite(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    private static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static String extensionOf(Path path) {
        if (path.getFileName() == null) return "";
        String name = path.getFileName().toString();
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

    private static String toJson(String id, int order, int number, Snapshot s, Parts parts, String extension) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("id", id);
        root.addProperty("order", order);
        root.addProperty("number", number);
        root.addProperty("wallpaper", extension);
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
        a.addProperty("panelHeightAutomatic", s.panelHeightAutomatic());
        a.addProperty("panelWidthAutomatic", s.panelWidthAutomatic());
        a.addProperty("uiScale", s.uiScale());
        a.addProperty("contentScale", s.contentScale());
        a.addProperty("cardTint", s.cardTint());
        a.addProperty("cardBlurStrength", s.cardBlurStrength());
        a.addProperty("blurStrength", s.panelBlurStrength());
        a.addProperty("cornerRadius", s.cornerRadius());
        a.addProperty("blurEnabled", s.blurEnabled());
        a.addProperty("panelOutline", s.panelOutline());
        a.addProperty("panelOutlineWidth", s.panelOutlineWidth());
        a.addProperty("animationEnabled", s.animationEnabled());
        a.addProperty("pageNavStyle", s.pageNavStyle());
        a.addProperty("themeName", s.themeName());
        a.addProperty("bubbleStyle", s.bubbleStyle());
        parts.write(a);
        root.add("appearance", a);
        return root.toString();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
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
