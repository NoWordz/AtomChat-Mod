package com.atom.chat.render;

import com.atom.chat.AtomChat;

/**
 * 这台机器上 Skija 的原生库到底能不能用 —— 正面探测一次，答案留给所有要问的人。
 *
 * <p><strong>为什么不看 {@code os.name}。</strong>每个发行包仅带 Windows x64 或 Linux x64 的一份原生库，
 * 而"包里有没有那一份"和"这台机器加载得起来没有"是两件事：
 * 换了架构、缺了系统的图形库、或者被别的东西抢先占了库名，平台自述都对不上。
 * 所以探的是能力本身 —— 真的去加载一次。这和 0.2.11 里 LAF 那个守卫是同一个道理。
 *
 * <p><strong>为什么必须在碰面板之前问。</strong>Skija 的失败落在原生层。
 * {@code UnsatisfiedLinkError} 还能被 try/catch 接住，但"原生句柄被释放了还去用"接不住 ——
 * 那是 JVM 直接死（0.2.11 的 hs_err_pid53868 就是，try/catch 连看一眼的机会都没有）。
 * 探测是知道这件事的唯一时机：等到玩家按下开面板的键，第一笔绘制就已经在原生层了。
 *
 * <p><strong>降级是什么。</strong>原生库不在，面板就不开 —— 给玩家一个必崩的界面不叫降级。
 * 原版聊天不受影响：接管聊天键的那个 mixin 在拿不到原生库时**不 cancel**，让原版照常接。
 *
 * <p>结果只算一次并记住：探测本身要几十到两百毫秒（真机实测 41 ms / 198 ms），
 * 而问它的人不止一个（环境摘要那一行、开面板的两个入口）。
 */
public final class SkiaSupport {

    /** 一次探测的结果；{@code reason} 只在失败时非 null。 */
    public record Probe(boolean available, long millis, String reason) {
    }

    /**
     * 探测动作：真的把原生库加载一次，加载不起来就抛。
     *
     * <p><strong>可注入</strong>，因为"这台机器没有原生库"这条分支在真机上养不出来：
     * CI 是 Linux，而测试类路径里恰好放着 linux 原生库（见 {@code build.gradle} 的
     * {@code testImplementation skija-linux-x64}）。
     */
    @FunctionalInterface
    public interface Natives {
        void load() throws Throwable;
    }

    private static final String LIBRARY = "io.github.humbleui.skija.impl.Library";

    /**
     * 唯一的一份结果。单测会把它钉成"没有原生库"，好让降级那条分支真的被走一遍 ——
     * 真机上走不到，而它恰恰是这一版要保证的东西。
     */
    static volatile Probe decided;

    private SkiaSupport() {
    }

    /**
     * 探测一次并记住。第一次调用真的加载，之后返回同一个结果。
     *
     * <p>失败的时候顺手把那一行日志打掉：这是玩家（和接手 issue 的人）唯一会看到的东西，
     * 所以它不带堆栈、只一行、且全是 ASCII。
     */
    public static Probe probe() {
        Probe current = decided;
        if (current == null) {
            synchronized (SkiaSupport.class) {
                current = decided;
                if (current == null) {
                    current = probe(SkiaSupport::loadBundled);
                    if (!current.available()) {
                        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", unavailableLine(System.getProperty("os.name"),
                                System.getProperty("os.arch"), current.reason())));
                    }
                    decided = current;
                }
            }
        }
        return current;
    }

    /**
     * 面板能不能开。**整个降级就是这一句**：原生库不在就永远不开。
     *
     * <p>开面板只有两个入口（聊天键被 mixin 接管、以及打开面板的键位），两处都问这里。
     */
    public static boolean available() {
        return probe().available();
    }

    /** 不缓存的那一份：探一次，把结果原样交出来（含耗时）。 */
    static Probe probe(Natives natives) {
        long startNs = System.nanoTime();
        String reason = null;
        try {
            natives.load();
        } catch (Throwable t) {
            reason = describe(t);
        }
        long millis = (System.nanoTime() - startNs) / 1_000_000L;
        return new Probe(reason == null, millis, reason);
    }

    /**
     * 拿不到原生库时的那一行。**纯函数**：平台与原因都由参数给，于是"Linux 上缺库"这条
     * 分支能在单测里拼出来，不用真去找一台 Linux。
     *
     * <p>三件事必须同时在这句话里：哪台机器、为什么、以及"原版聊天没被动过" ——
     * 少了最后一句，读到它的人会以为整个模组废了。全程 ASCII：日志文件按平台默认编码写
     * （中文 Windows 实测 GBK），而这份日志会被贴进 UTF-8 的 issue。
     */
    static String unavailableLine(String os, String arch, String reason) {
        return "Skija natives unavailable on " + text(os) + "/" + text(arch) + " (" + text(reason)
                + "): the AtomChat panel stays disabled and vanilla chat is unchanged. "
                + "Available native packages: Windows x64 and Linux x64 only; macOS/ARM64 are not packaged. "
                + "Linux also needs EGL/GL system libraries.";
    }

    /** 真加载：就是我们自己包里那一个。 */
    private static void loadBundled() throws Throwable {
        loadLibrary(LIBRARY, SkiaSupport.class.getClassLoader());
    }

    /**
     * 反射加载一个 Skija 的 {@code Library} 类：找类、调 {@code staticLoad()}、读 {@code _loaded}。
     *
     * <p>类名是参数而不是写死，是为了让这一段本身可测：拿一个假的库类，就能把
     * "调了、没抛，但 {@code _loaded} 还是 false"这种最难察觉的失败喂出来。
     */
    static void loadLibrary(String name, ClassLoader loader) throws Throwable {
        Class<?> library = Class.forName(name, true, loader);
        try {
            library.getMethod("staticLoad").invoke(null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 反射会把真正的原因包一层，而那一层里没有 "no skija in java.library.path" ——
            // 不解开，日志上写的就永远是 "InvocationTargetException"，等于没说。
            throw e.getCause() == null ? e : e.getCause();
        }
        if (!(Boolean) library.getField("_loaded").get(null)) {
            // 不抛异常、也不说加载好了 —— 那就是没加载好。宁可在这里说不，也别等到绘制时。
            throw new IllegalStateException("Library.staticLoad() finished with _loaded=false");
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank()
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
