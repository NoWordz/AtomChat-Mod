package com.atom.chat.settings;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackLanguageTest {
    @Test void aboutTutorialExplainsWhereDiagnosticLoggingIsEnabled() throws Exception {
        Path root=Path.of(System.getProperty("atomchat.repoRoot","../..")).toAbsolutePath().normalize();
        for(String target:new String[]{"1.20.1-forge","1.21.1-fabric","1.21.1-neoforge"}) {
            var zh=JsonParser.parseString(Files.readString(root.resolve("platforms/"+target+"/src/main/resources/assets/atomchat/lang/zh_cn.json"))).getAsJsonObject();
            var en=JsonParser.parseString(Files.readString(root.resolve("platforms/"+target+"/src/main/resources/assets/atomchat/lang/en_us.json"))).getAsJsonObject();
            for (var language : java.util.List.of(zh, en)) {
                for (String key : java.util.List.of("atomchat.tutorial.about.body", "atomchat.tutorial.settings.about.about")) {
                    String guide = language.get(key).getAsString().toLowerCase(java.util.Locale.ROOT);
                    assertTrue(guide.contains(language.get("atomchat.settings.group.about.maintenance")
                            .getAsString().toLowerCase(java.util.Locale.ROOT)));
                    assertTrue(guide.contains(language.get("atomchat.settings.about.debug")
                            .getAsString().toLowerCase(java.util.Locale.ROOT)));
                }
            }
        }
    }
    @Test void allSixCatalogsContainTheActualOutlineAndWallpaperKeys() throws Exception {
        Path root=Path.of(System.getProperty("atomchat.repoRoot","../..")).toAbsolutePath().normalize();
        for(String target:new String[]{"1.20.1-forge","1.21.1-fabric","1.21.1-neoforge"})
            for(String locale:new String[]{"en_us","zh_cn"}) {
                var json=JsonParser.parseString(Files.readString(root.resolve("platforms/"+target+"/src/main/resources/assets/atomchat/lang/"+locale+".json"))).getAsJsonObject();
                for(String key:new String[]{"atomchat.settings.appearance.outline_width","atomchat.settings.appearance.outline_width.desc","atomchat.settings.wallpaper.cobalt","atomchat.settings.wallpaper.none","atomchat.settings.wallpaper.custom"})
                    assertTrue(json.has(key) && !json.get(key).getAsString().isBlank(),target+"/"+locale+": "+key);
                assertFalse(json.get("atomchat.settings.appearance.outline.desc").getAsString().toLowerCase().contains("white"),"obsolete white-ring copy");
            }
    }
}
