package com.atom.chat.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 源码接线守卫：三端都必须真的调用预热，且屏幕必须在首开路径上打点。
 * 这是「把预热带去掉」变异会变红的那条线。
 */
class FirstOpenPrewarmWiringTest {

    private static final List<String> CLIENTS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/AtomChatClient.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/AtomChatClient.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChatClient.java");
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java");
    private static final List<String> RENDERERS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    private static String src(String rel) throws Exception {
        return Files.readString(root().resolve(rel));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    @Test void everyClientStartsTheColdFontAndJoinWarmups() throws Exception {
        for (String rel : CLIENTS) {
            String s = src(rel);
            assertEquals(1, count(s, "FirstOpenWarmup.prewarmFontsAsync();"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.prewarmEmojiAsync(firstOpenWarmupTexts())"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.requestGlWarmup();"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.consumeGlWarmup()"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.onDisconnect();"), rel);
            assertTrue(s.contains("MinecraftRenderBridge.get().prepare()"), rel + ": GL 预热必须真的建桥");
            assertTrue(s.contains("PanelBlurRenderer.prewarm("), rel + ": 模糊纹理必须真的预分配");
        }
    }

    @Test void everyScreenMeasuresTheFirstOpen() throws Exception {
        for (String rel : SCREENS) {
            String s = src(rel);
            assertEquals(1, count(s, "FirstOpenBudget.beginFirstOpen("), rel);
            assertEquals(1, count(s, "FirstOpenBudget.endFrame()"), rel);
            assertTrue(count(s, "FirstOpenBudget.active()") >= 1, rel + ": 至少 icon 段要计时");
        }
    }

    @Test void everyBlurRendererCanPrewarmItsTextures() throws Exception {
        for (String rel : RENDERERS) {
            String s = src(rel);
            assertEquals(1, count(s, "public static void prewarm("), rel);
            assertTrue(s.contains("ensureTextures(widthPx, heightPx, false)"), rel);
            assertTrue(s.contains("FirstOpenBudget.timeBlurInit("), rel + ": 分配段必须可归因");
        }
    }
}
