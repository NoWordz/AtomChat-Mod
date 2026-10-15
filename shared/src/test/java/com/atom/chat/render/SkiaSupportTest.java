package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 非 Windows 上"失败得干净"那一条：探测拿不到原生库，面板就不开，原版聊天照常。
 *
 * <p>为什么这条分支必须靠注入喂出来：CI 是 Linux，而测试类路径里恰好放着
 * {@code skija-linux-x64}（见 build.gradle 的 testImplementation）—— 也就是说
 * **运行测试的机器上原生库永远在**，"没有原生库"这件事在真机上造不出来。
 * 而它恰恰是这一版要保证的东西，所以它必须是可喂的。
 */
class SkiaSupportTest {

    /** 假的 skija 库：静态加载这一段（反射 + {@code _loaded} 检查）真的跑一遍。 */
    public static final class LoadsFine {
        public static boolean _loaded = true;

        public static void staticLoad() {
        }
    }

    public static final class LoadsButStaysUnloaded {
        public static boolean _loaded = false;

        public static void staticLoad() {
        }
    }

    public static final class ThrowsOnLoad {
        public static boolean _loaded = false;

        public static void staticLoad() {
            throw new UnsatisfiedLinkError("no skija in java.library.path");
        }
    }

    @Test
    void anActionThatThrowsIsReportedUnavailable() {
        SkiaSupport.Probe probe = SkiaSupport.probe(() -> {
            throw new UnsatisfiedLinkError("no skija in java.library.path");
        });
        assertFalse(probe.available(), "抛了就不该说能用");
        assertEquals("UnsatisfiedLinkError: no skija in java.library.path", probe.reason());
        assertTrue(probe.millis() >= 0L, "耗时不能是负数");
    }

    @Test
    void anActionThatReturnsNormallyCountsAsLoaded() {
        // 判定就是"抛没抛"：真实来源里 {@code _loaded=false} 是被 loadLibrary 转成异常的，
        // 所以这一层不需要自己再判断一次。
        SkiaSupport.Probe probe = SkiaSupport.probe(() -> {
        });
        assertTrue(probe.available(), "动作正常返回就算加载好了");
        assertNull(probe.reason(), "成功不该带原因");
    }

    @Test
    void aLibraryThatFinishesWithNothingLoadedIsUnavailable() {
        SkiaSupport.Probe probe = SkiaSupport.probe(() -> SkiaSupport.loadLibrary(
                LoadsButStaysUnloaded.class.getName(), LoadsButStaysUnloaded.class.getClassLoader()));
        assertFalse(probe.available(), "staticLoad 回来了但 _loaded 是 false —— 那就是没加载好");
        assertTrue(probe.reason().contains("_loaded=false"), probe.reason());
    }

    @Test
    void aLibraryThatLoadsIsAvailable() throws Throwable {
        SkiaSupport.Probe probe = SkiaSupport.probe(() -> SkiaSupport.loadLibrary(
                LoadsFine.class.getName(), LoadsFine.class.getClassLoader()));
        assertTrue(probe.available(), "加载成功就该说能用");
        assertNull(probe.reason());
    }

    @Test
    void aLibraryThatThrowsOnLoadIsUnavailable() {
        SkiaSupport.Probe probe = SkiaSupport.probe(() -> SkiaSupport.loadLibrary(
                ThrowsOnLoad.class.getName(), ThrowsOnLoad.class.getClassLoader()));
        assertFalse(probe.available());
        assertTrue(probe.reason().startsWith("UnsatisfiedLinkError"), probe.reason());
    }

    /**
     * 降级判据本身：探测说没有，面板就不开。开面板的两个入口读的都是这一句，
     * 而"开出来"在那些平台上等于是必崩而不是难看。
     */
    @Test
    void thePanelStaysClosedWhenTheProbeSaidNo() {
        pin(new SkiaSupport.Probe(false, 3L, "UnsatisfiedLinkError: no skija in java.library.path"));
        try {
            assertFalse(SkiaSupport.available(), "没有原生库就不许开面板");
        } finally {
            pin(null);
        }
    }

    /** 反面同样要守：永远答 false 等于把这个模组在 Windows 上也关掉了。 */
    @Test
    void thePanelOpensWhenTheProbeSaidYes() {
        pin(new SkiaSupport.Probe(true, 41L, null));
        try {
            assertTrue(SkiaSupport.available(), "原生库在就该照常开");
        } finally {
            pin(null);
        }
    }

    @Test
    void theProbeOnlyHappensOnce() {
        pin(null);
        assertSame(SkiaSupport.probe(), SkiaSupport.probe(), "探测要记住结果，不能每次问都重来一次");
    }

    @Test
    void theUnavailableLineNamesThePlatformTheReasonAndWhatStillWorks() {
        String line = SkiaSupport.unavailableLine("Linux", "amd64", "UnsatisfiedLinkError: no skija");
        assertTrue(line.contains("Linux/amd64"), line);
        assertTrue(line.contains("UnsatisfiedLinkError: no skija"), line);
        assertTrue(line.contains("vanilla chat is unchanged"), line);
        assertTrue(line.contains("Windows x64 and Linux x64 only"), line);
        assertTrue(line.contains("macOS/ARM64 are not packaged"), line);
        assertTrue(line.contains("Linux also needs EGL/GL system libraries"), line);
        assertFalse(line.contains("Windows x64 native only"), line);
    }

    /**
     * 日志文件是按平台默认编码写的（中文 Windows 实测 GBK），而这一行会被贴进 UTF-8 的 issue；
     * 而且它必须是**一行** —— 拿不到原生库时这是玩家与排查者唯一会看到的东西。
     */
    @Test
    void theUnavailableLineIsOneAsciiLine() {
        String line = SkiaSupport.unavailableLine(System.getProperty("os.name"),
                System.getProperty("os.arch"), "UnsatisfiedLinkError: no skija in java.library.path");
        assertTrue(line.chars().allMatch(c -> c < 0x80), line);
        assertFalse(line.contains("\n"), line);
        assertFalse(line.contains("\r"), line);
    }

    /** 属性都取不到时也得是一句能读的话，不能出现 "null/null"。 */
    @Test
    void theUnavailableLineSurvivesMissingProperties() {
        String line = SkiaSupport.unavailableLine(null, "  ", null);
        assertTrue(line.contains("unknown/unknown"), line);
        assertTrue(line.chars().allMatch(c -> c < 0x80), line);
    }

    /** 钉住结果（或者松开）。单测没有别的办法把"这台机器没有原生库"造出来。 */
    private static void pin(SkiaSupport.Probe probe) {
        SkiaSupport.decided = probe;
    }
}
