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
            // The emoji warmup follows the backlog, not the join event: at join
            // the chat is still empty, so a join-triggered warmup silently did
            // nothing and the first panel frame rasterized every emoji on the
            // render thread (the 150 ms spike).
            assertEquals(1, count(s, "FirstOpenWarmup.pollEmojiPrewarm("), rel);
            assertEquals(0, count(s, "FirstOpenWarmup.prewarmEmojiAsync("),
                    rel + ": 旧的 join 触发必须已经没有了");
            assertEquals(1, count(s, "FirstOpenWarmup.requestGlWarmup();"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.consumeGlWarmup()"), rel);
            assertEquals(1, count(s, "FirstOpenWarmup.onDisconnect();"), rel);
            assertTrue(s.contains("MinecraftRenderBridge.get().prepare()"), rel + ": GL 预热必须真的建桥");
            assertTrue(s.contains("PanelBlurRenderer.prewarm()"), rel + ": 模糊 shader 必须真的预编译");
        }
    }

    @Test void everyScreenMeasuresTheFirstOpen() throws Exception {
        for (String rel : SCREENS) {
            String s = src(rel);
            assertEquals(1, count(s, "FirstOpenBudget.beginFirstOpen("), rel);
            assertEquals(1, count(s, "FirstOpenBudget.endFrame()"), rel);
            assertEquals(1, count(s, "FirstOpenBudget.markDrawStart()"), rel + ": 首帧必须单独计时");
            assertEquals(1, count(s, "FirstOpenBudget.addFirstDraw("), rel + ": 首帧必须单独归因");
            assertTrue(count(s, "FirstOpenBudget.active()") >= 1, rel + ": 至少 icon 段要计时");
        }
    }

    @Test void everyBlurRendererPrewarmsShadersButNeverTextures() throws Exception {
        for (String rel : RENDERERS) {
            String s = src(rel);
            assertEquals(1, count(s, "public static void prewarm("), rel);
            assertFalse(s.contains("ensureTextures(widthPx, heightPx, false)"), rel
                    + ": 预热不得分配纹理——容量下限只涨不缩，会让正式路径的纹理永久偏大");
            assertTrue(s.contains("FirstOpenBudget.timeBlurInit("), rel + ": 分配段必须可归因");
            // Fabric 懒编译 shader，预热必须真的把它编译掉；Forge/NeoForge 的
            // shader 由启动期注册回调准备好，预热自然无事可做——但两种情况都
            // 不许退回「预热里分配纹理」。
            boolean lazyShaders = s.contains("ensureLoaded();");
            boolean registeredAtStartup = s.contains("注册回调");
            assertTrue(lazyShaders || registeredAtStartup,
                    rel + ": 预热要么编译 shader，要么说明 shader 是启动期注册好的");
        }
    }
}
