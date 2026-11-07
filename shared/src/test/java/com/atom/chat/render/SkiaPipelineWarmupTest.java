package com.atom.chat.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 首帧管线预热的门、失败隔离与"编译的是同一套程序"约束。
 *
 * <p>不调用原生 Skia：绘制内容正确性由实机 first_open_budget / PanelSegments 验证，
 * 这里只钉死调度语义与源码契约。
 */
class SkiaPipelineWarmupTest {

    @BeforeEach void reset() {
        SkiaPipelineWarmup.resetForTest();
        System.clearProperty(SkiaPipelineWarmup.PROPERTY);
    }

    @AfterEach void clean() {
        SkiaPipelineWarmup.resetForTest();
        System.clearProperty(SkiaPipelineWarmup.PROPERTY);
    }

    @Test void runsExactlyOncePerProcess() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(SkiaPipelineWarmup.runOnce(calls::incrementAndGet), "首次应真的预热");
        assertFalse(SkiaPipelineWarmup.runOnce(calls::incrementAndGet), "第二次必须静默跳过");
        assertEquals(1, calls.get());
        assertNull(SkiaPipelineWarmup.lastFailure());
    }

    @Test void failureIsContainedAndNeverRetries() {
        assertFalse(SkiaPipelineWarmup.runOnce(() -> {
            throw new IllegalStateException("no GL context");
        }), "失败不得外泄到 client tick");
        assertNotNull(SkiaPipelineWarmup.lastFailure(), "失败必须可诊断");
        AtomicInteger calls = new AtomicInteger();
        assertFalse(SkiaPipelineWarmup.runOnce(calls::incrementAndGet), "失败后不得每个 tick 重试");
        assertEquals(0, calls.get());
    }

    @Test void killSwitchDisablesTheWarmupWithoutBurningTheGate() {
        System.setProperty(SkiaPipelineWarmup.PROPERTY, "false");
        AtomicInteger calls = new AtomicInteger();
        assertFalse(SkiaPipelineWarmup.runOnce(calls::incrementAndGet));
        assertEquals(0, calls.get());
        System.clearProperty(SkiaPipelineWarmup.PROPERTY);
        assertTrue(SkiaPipelineWarmup.runOnce(calls::incrementAndGet),
                "关掉再打开后仍应能跑一次（安全阀不烧门）");
        assertEquals(1, calls.get());
    }

    @Test void warmupFrameMatchesTheMainSurfaceAndDrawsVisibleContent() throws Exception {
        String s = Files.readString(root().resolve(
                "shared/src/main/java/com/atom/chat/render/SkiaPipelineWarmup.java"));
        assertTrue(s.contains("ImageInfo.makeN32Premul"), "colorType/alphaType 必须与主 surface 一致");
        assertTrue(s.contains("ColorSpace.getSRGB()"), "colorSpace 必须与主 surface 一致");
        assertTrue(s.contains("SurfaceOrigin.BOTTOM_LEFT"), "origin 必须与主 surface 一致");
        assertTrue(s.contains("flushAndSubmit(surface, true)"), "必须同步等 GPU 完成编译");
        assertTrue(s.contains("drawPicture("), "必须命中 _nDrawPicture 这条最大热点路径");
        assertTrue(s.contains("SkiaFontRenderer.drawText("), "必须画真实文字以填充字形图集");
        assertFalse(s.contains("setARGB(0,"), "alpha=0 会命中 nothingToDraw，编译不了任何管线");
        assertFalse(s.contains("makeGL()"), "不得自建第二个 DirectContext");
    }

    @Test void everyClientBorrowsTheBridgeContext() throws Exception {
        for (String rel : List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/AtomChatClient.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/AtomChatClient.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChatClient.java")) {
            String s = Files.readString(root().resolve(rel));
            assertEquals(1, count(s, "SkiaPipelineWarmup.runOnce("), rel);
            assertTrue(s.contains("MinecraftRenderBridge.get().withContext("),
                    rel + ": 必须借桥的唯一 DirectContext");
        }
    }

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
        return n;
    }
}
