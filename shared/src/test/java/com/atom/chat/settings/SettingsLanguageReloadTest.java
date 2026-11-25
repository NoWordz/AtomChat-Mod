package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLanguageReloadTest {
    private static final UiLayout LAYOUT = UiLayout.ofDetail(20, 20, 360, 840);

    @Test
    void openSettingsFollowLanguageOnlyReloads(@TempDir Path root) throws Exception {
        var provider = Platform.class.getDeclaredField("provider");
        provider.setAccessible(true);
        var originalProvider = provider.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String modId) { return false; }
        });
        var config = AtomChatConfig.get();
        boolean animations = config.animationEnabled;
        config.animationEnabled = false;

        try (var language = new LanguageOnlyReload(root.resolve("pack"))) {
            language.select("zh_cn");
            SettingsSectionPage.clearTranslationCache();
            var views = List.of(
                    new View(SettingsSection.APPEARANCE, 0, "画质", "Graphics quality"),
                    new View(SettingsSection.APPEARANCE, 1, "面板背景", "Panel background"),
                    new View(SettingsSection.ABOUT, -1, "第三方组件", "Third-party components"),
                    new View(SettingsSection.CHAT, 1, "历史存储", "History storage"),
                    new View(SettingsSection.PRIVACY, -1, "屏蔽名单", "Blocked players"));
            for (int frame = 0; frame < 32; frame++) {
                views.get(0).page().warmAppearanceTheme(LAYOUT);
            }
            var chinese = views.stream().map(View::capture).toList();

            // Chloride calls only the language manager, skipping mod resource reload listeners.
            language.select("en_us");
            var switched = views.stream().map(View::capture).toList();
            SettingsSectionPage.clearTranslationCache();
            var english = views.stream().map(View::capture).toList();

            language.select("zh_cn");
            var switchedBack = views.stream().map(View::capture).toList();
            assertAll(java.util.stream.IntStream.range(0, views.size()).mapToObj(i -> () -> {
                var view = views.get(i);
                assertAll(view.english(),
                        () -> assertTrue(chinese.get(i).titles().contains(view.chinese()), chinese.get(i).titles()::toString),
                        () -> assertTrue(english.get(i).titles().contains(view.english()), english.get(i).titles()::toString),
                        () -> assertTrue(switched.get(i).titles().contains(view.english()), switched.get(i).titles()::toString),
                        () -> assertTrue(Arrays.equals(english.get(i).pixels(), switched.get(i).pixels()),
                                "Language-only reload should paint the same English body as a full invalidation"),
                        () -> assertTrue(Arrays.equals(chinese.get(i).pixels(), switchedBack.get(i).pixels()),
                                "Switching back should paint the original Chinese body"));
            }));
        } finally {
            SettingsSectionPage.clearTranslationCache();
            config.animationEnabled = animations;
            provider.set(null, originalProvider);
        }
    }

    private record View(SettingsSectionPage page, SettingsSection section, String chinese, String english) {
        View(SettingsSection section, int tab, String chinese, String english) {
            this(new SettingsSectionPage(), section, chinese, english);
            page.selectChip(section, tab);
        }

        Snapshot capture() {
            try (var surface = Surface.makeRasterN32Premul(400, 880)) {
                page.measureContent(LAYOUT, section);
                UiStagger.atFrame(1000, () -> page.renderBody(surface.getCanvas(), LAYOUT, section,
                        -100, -100, 0, 0xff4085eb));
                var titles = page.tutorialRows(section).stream().map(SettingsSectionPage.TutorialRow::title).toList();
                try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                    return new Snapshot(titles, data.getBytes());
                }
            }
        }
    }

    private record Snapshot(List<String> titles, byte[] pixels) {}

    /** Runs the real Minecraft client language reload against this target's classpath resources. */
    private static final class LanguageOnlyReload implements AutoCloseable {
        private final AutoCloseable resources;
        private final Object manager;
        private final Object originalLanguage;
        private final Method select;
        private final Method reload;
        private final Method setLanguage;
        private final Method setI18n;

        LanguageOnlyReload(Path packRoot) throws Exception {
            Path assets = Files.createDirectories(packRoot.resolve("assets/atomchat/lang"));
            for (String locale : List.of("en_us", "zh_cn")) {
                try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/assets/atomchat/lang/" + locale + ".json"))) {
                    Files.copy(stream, assets.resolve(locale + ".json"));
                }
            }
            Files.writeString(packRoot.resolve("pack.mcmeta"), """
                    {"language":{"zh_cn":{"region":"China","name":"Chinese","bidirectional":false}}}
                    """);

            boolean yarn = SettingsSectionPage.class.getResource("/net/minecraft/util/Language.class") != null;
            String lang = yarn ? "net.minecraft.client.resource.language." : "net.minecraft.client.resources.language.";
            String pack = yarn ? "net.minecraft.resource." : "net.minecraft.server.packs.";
            String managerPackage = yarn ? pack : pack + "resources.";
            Class<?> language = Class.forName(yarn ? "net.minecraft.util.Language" : "net.minecraft.locale.Language");
            originalLanguage = language.getMethod("getInstance").invoke(null);
            setLanguage = language.getMethod(yarn ? "setInstance" : "inject", language);
            setI18n = Class.forName(lang + "I18n").getDeclaredMethod("setLanguage", language);
            setI18n.setAccessible(true);

            Class<?> directory = Class.forName(pack + (yarn ? "DirectoryResourcePack" : "PathPackResources"));
            Class<?> languageManager = Class.forName(lang + "LanguageManager");
            Object resourcePack;
            if (directory.getConstructors()[0].getParameterCount() == 3) {
                resourcePack = directory.getConstructor(String.class, Path.class, boolean.class)
                        .newInstance("settings-locales", packRoot, false);
                manager = languageManager.getConstructor(String.class).newInstance("en_us");
            } else {
                Class<?> info = Class.forName(pack + (yarn ? "ResourcePackInfo" : "PackLocationInfo"));
                Class<?> text = Class.forName(yarn ? "net.minecraft.text.Text" : "net.minecraft.network.chat.Component");
                Class<?> source = Class.forName(pack + (yarn ? "ResourcePackSource" : "repository.PackSource"));
                Object title = text.getMethod("literal", String.class).invoke(null, "Settings locales");
                Object location = info.getConstructor(String.class, text, source, Optional.class)
                        .newInstance("settings-locales", title, source.getField(yarn ? "NONE" : "DEFAULT").get(null), Optional.empty());
                resourcePack = directory.getConstructor(info, Path.class).newInstance(location, packRoot);
                manager = languageManager.getConstructor(String.class, Consumer.class)
                        .newInstance("en_us", (Consumer<Object>) ignored -> {});
            }
            Class<?> type = Class.forName(pack + (yarn ? "ResourceType" : "PackType"));
            Class<?> resourceManager = Class.forName(managerPackage + (yarn ? "LifecycledResourceManagerImpl" : "MultiPackResourceManager"));
            resources = (AutoCloseable) resourceManager.getConstructor(type, List.class)
                    .newInstance(type.getField("CLIENT_RESOURCES").get(null), List.of(resourcePack));
            select = languageManager.getMethod(yarn ? "setLanguage" : "setSelected", String.class);
            reload = languageManager.getMethod(yarn ? "reload" : "onResourceManagerReload",
                    Class.forName(managerPackage + "ResourceManager"));
        }

        void select(String locale) throws Exception {
            select.invoke(manager, locale);
            reload.invoke(manager, resources);
        }

        @Override public void close() throws Exception {
            try (resources) {
                setLanguage.invoke(null, originalLanguage);
                setI18n.invoke(null, originalLanguage);
            }
        }
    }
}
