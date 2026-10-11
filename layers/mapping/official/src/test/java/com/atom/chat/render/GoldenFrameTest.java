package com.atom.chat.render;

import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.page.ConversationListPage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.page.PageHost;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsColor;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.QuickPhrasePanel;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基准帧（golden frames）：把每个页面的「静止帧」拍成 PNG，供拆分前后逐字节对拍。
 *
 * <p>与 {@link OffscreenRenderTest} 的分工：那个测「不抛异常、save/restore 配平、画了东西」，
 * 这个测「画出来的每一个字节和上次一模一样」。阶段 2 每拆一块的像素门就是这里：
 * 拆前 {@code goldenCapture}，拆后 {@code goldenCompare} → differing pixels 必须为 0。
 *
 * <p>为什么不进 CI / 不进 check：Skia 的 CPU 光栅不保证跨机器、跨 Skija 版本逐字节一致，
 * CI 上也没有「拆前」可比。所以它是**本机对照工具**，{@code golden/} 已 gitignore。
 *
 * <p>三种模式（系统属性 {@code atomchat.golden.mode}，由 gradle 任务 goldenCapture /
 * goldenCompare / goldenDeterminism 写进来；普通 {@code test} 跑时是 off → 本类跳过）：
 * <ul>
 *   <li>{@code capture} —— 每页拍一帧写 {@code golden/<目标>/<页>.png}</li>
 *   <li>{@code compare} —— 重渲一帧，与基准帧逐字节比；不等则解码两边，报 differing pixels 与最大通道差</li>
 *   <li>{@code determinism} —— 同一 JVM 里连拍两次，逐页断言 PNG 逐字节相同（确定性实证）</li>
 * </ul>
 *
 * <p>确定性从哪来：固定种子数据（固定文本 / UUID / **固定时间戳**，不用 now）、页面对象每趟只建
 * 一次（动画才有机会落定）、每页先推热身帧再 sleep 10× 该页最长动画、最后一帧才进 PNG。
 * 没有这三步，对拍门只会周期性地假红。
 */
class GoldenFrameTest {
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;
    private static final int ACCENT = 0xFF3B82F6;

    /** 推帧间隔：与真实帧率同量级，让页面的时间驱动动画真的往前走。 */
    private static final long FRAME_MS = 40L;
    /** 热身帧数：把 hover / 入场这类渐近动画推到接近目标。 */
    private static final int WARM_FRAMES = 10;
    /** 固定种子时间戳基准（2026-01-01T00:00:00Z）：所有消息都用它加常量。 */
    private static final long SEED_EPOCH_MS = 1767225600000L;
    /** 裁剪器解码等待：后台线程解码夹具图（毫秒级），给足余量后再拍帧。 */
    private static final long DECODE_WAIT_MS = 1500L;

    /**
     * 夹具目录**必须稳定**（不能用 @TempDir）：页面若画出路径，随机目录名会污染基准帧。
     * 优先落在工程目录下的 build/（构建脚本通过系统属性给工程目录），这样 neo 的
     * build/minecraft-junit 工作目录也不会把夹具挪到别处。
     */
    private static final Path FIXTURE = fixtureRoot();

    private static Path fixtureRoot() {
        String projectDir = System.getProperty("atomchat.projectDir", "");
        Path base = projectDir.isEmpty() ? Path.of("") : Path.of(projectDir);
        return base.resolve("build").resolve("golden-fixture").toAbsolutePath();
    }

    private static final String MODE = System.getProperty("atomchat.golden.mode", "off");

    @BeforeAll
    static void installPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return FIXTURE.resolve("config");
            }

            @Override
            public Path gameDir() {
                return FIXTURE.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /**
     * 一个页面：名字 + 该页最长动画时长（settle = 10×）+ 一帧的绘制入口。
     *
     * <p>{@code prepare} 在这一页推帧**之前**跑一次，用来制造「刚发生」的状态：
     * 横幅入栈、裁剪器开图。放到清单构建期做不行 —— 前面几页各要 2 秒多，
     * 轮到横幅时它早就过期了（实测：一帧 0 像素）。
     */
    private record Page(String name, long longestAnimMs, Runnable prepare, Consumer<Canvas> render) {
    }

    @Test
    void goldenFrames() throws Exception {
        Assumptions.assumeTrue(!"off".equals(MODE),
                "基准帧只在 goldenCapture / goldenCompare / goldenDeterminism 下跑"
                        + "（CI 不跑：跨机器不保证逐字节一致，也没有「拆前」可比）");
        Path outDir = goldenDir();
        switch (MODE) {
            case "capture" -> {
                Files.createDirectories(outDir);
                for (Page page : pages()) {
                    byte[] png = settleAndEncode(page);
                    Files.write(outDir.resolve(page.name() + ".png"), png);
                    System.out.println("[golden] capture " + page.name() + " → " + png.length + " bytes");
                }
            }
            case "determinism" -> {
                Path detDir = outDir.resolve("determinism");
                Files.createDirectories(detDir.resolve("pass1"));
                Files.createDirectories(detDir.resolve("pass2"));
                System.out.println("[golden] 同一 JVM 连拍两次（逐页逐字节）：");
                for (String name : pageNames()) {
                    byte[] first = settleAndEncode(freshPage(name));
                    byte[] second = settleAndEncode(freshPage(name));
                    Files.write(detDir.resolve("pass1").resolve(name + ".png"), first);
                    Files.write(detDir.resolve("pass2").resolve(name + ".png"), second);
                    System.out.println("  " + (java.util.Arrays.equals(first, second) ? "SAME  " : "DIFFER")
                            + "  " + name + "  " + first.length + "/" + second.length + " bytes");
                    assertArrayEquals(first, second, name
                            + " 同一 JVM 连拍两次不一致 —— 这一页不许进基准帧清单");
                }
            }
            case "compare" -> {
                for (Page page : pages()) {
                    Path golden = outDir.resolve(page.name() + ".png");
                    assertTrue(Files.isRegularFile(golden),
                            "缺基准帧 " + golden + " —— 先跑 goldenCapture 拍一版");
                    byte[] fresh = settleAndEncode(page);
                    byte[] expected = Files.readAllBytes(golden);
                    if (!java.util.Arrays.equals(expected, fresh)) {
                        int[] diff = pixelDiff(expected, fresh);
                        assertTrue(false, "基准帧不一致：" + page.name()
                                + " differing pixels=" + diff[0] + "，max channel delta=" + diff[1]
                                + "（golden " + expected.length + " bytes / fresh " + fresh.length + " bytes）");
                    }
                    System.out.println("[golden] compare " + page.name() + " → identical (" + fresh.length + " bytes)");
                }
            }
            default -> Assumptions.abort("未知的 atomchat.golden.mode=" + MODE);
        }
    }

    /** 推热身帧 → sleep 10× 该页最长动画 → 拍帧 → 编码 PNG；并断言这一帧真的画了东西。 */
    private static byte[] settleAndEncode(Page page) {
        page.prepare().run();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            Canvas canvas = surface.getCanvas();
            int baseline = canvas.getSaveCount();
            for (int frame = 0; frame < WARM_FRAMES; frame++) {
                canvas.clear(0);
                page.render().accept(canvas);
                assertEquals(baseline, canvas.getSaveCount(), page.name() + " 把 canvas save 栈弄失衡了");
                sleep(FRAME_MS);
            }
            sleep(page.longestAnimMs() * 10L);
            canvas.clear(0);
            page.render().accept(canvas);
            assertEquals(baseline, canvas.getSaveCount(), page.name() + " 把 canvas save 栈弄失衡了");
            assertTrue(drawnBytes(surface) > 0, page.name() + " 一帧下来一个像素都没画 —— 基准帧会是空的");
            try (Image snapshot = surface.makeImageSnapshot();
                 Data data = snapshot.encodeToData(EncodedImageFormat.PNG)) {
                assertNotNull(data, page.name() + " 编码 PNG 失败");
                return data.getBytes();
            }
        } finally {
            surface.close();
        }
    }

    /** 按名字重建一个页面（determinism 的第二趟用）。 */
    private static Page freshPage(String name) {
        for (Page page : pages()) {
            if (page.name().equals(name)) {
                return page;
            }
        }
        throw new IllegalStateException("页面清单里没有 " + name);
    }

    /** 页面名字清单（determinism 逐页重建，避免整趟清单里的对象被跨页复用）。 */
    private static List<String> pageNames() {
        List<String> names = new ArrayList<>();
        for (Page page : pages()) {
            names.add(page.name());
        }
        return names;
    }

    /**
     * 页面清单：现有离屏覆盖的 6 类 + 补页里能离屏跑的那几页。
     *
     * <p>页面对象**在清单里建好、被帧闭包捕获**：每帧新建对象会让动画每帧从零开始，
     * 帧永远停在入场的第一帧（既是假基准帧，也会「画了 0 像素」）。
     */
    private static List<Page> pages() {
        List<Page> pages = new ArrayList<>();

        SettingsHomePage home = new SettingsHomePage();
        UiLayout root = UiLayout.ofRoot(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        pages.add(new Page("settings-home", UiMotion.HOVER_MS, () -> {
        },
                canvas -> home.render(canvas, root, -1.0F, -1.0F, 0.0F)));

        for (SettingsSection section : SettingsSection.values()) {
            SettingsSectionPage page = new SettingsSectionPage();
            UiLayout layout = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            pages.add(new Page("settings-section-" + section.id(), UiMotion.HOVER_MS, () -> {
            },
                    canvas -> page.render(canvas, layout, section, -1.0F, -1.0F, 0.0F, ACCENT)));
        }

        List<ChatMessage> history = conversation();
        MessageListView view = new MessageListView(new OffscreenHost(openedAfter(history)));
        ScrollController scroll = new ScrollController();
        UiLayout listLayout = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        pages.add(new Page("message-list", UiMotion.MESSAGE_MS, () -> {
        }, canvas -> view.draw(canvas, listLayout.list.x(), listLayout.list.y(), listLayout.list.w(),
                listLayout.list.h(), history, scroll)));

        EmojiPanel emoji = new EmojiPanel(new OffscreenEmojiHost());
        UiLayout panelLayout = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        // 表情/快捷语面板挂在输入栏上方（panelY = inputBar.y() - gap - panelH），
        // 而 ofDetail 是**没有输入栏**的详情页布局（inputBar 是零矩形）——用它拍出来的
        // 「面板」会整块落在画面之上，只剩底边一道 10px 阴影（实测：1607 字节的基准帧）。
        // 所以这两页必须用 CHAT 模式布局（of(...)），面板才真的在画面里。
        UiLayout chatLayout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        pages.add(new Page("emoji-panel", UiMotion.TAB_MS, emoji::toggle, canvas -> {
            emoji.update(FRAME_MS);
            emoji.render(canvas, chatLayout, -1.0F, -1.0F, FRAME_MS);
        }));

        QuickPhrasePanel phrases = new QuickPhrasePanel(text -> {
        }, () -> ACCENT);
        pages.add(new Page("quick-phrase-panel", UiMotion.POPUP_MS, phrases::open, canvas -> {
            phrases.update(FRAME_MS);
            phrases.render(canvas, chatLayout, -1.0F, -1.0F, FRAME_MS);
        }));

        pages.add(new Page("notification-banner", UiMotion.MESSAGE_MS, () -> {
            // 每趟都先灌满一整栈：MAX_STACK 会把上一趟留下的旧横幅挤出去，
            // 于是「可见的那三条」在同一 JVM 的两次拍摄里是同一组（确定性）。
            for (int i = 0; i < 3; i++) {
                NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.MENTION, "Alice",
                        "row " + i, seededMessage("banner row " + i, false, i));
            }
        }, canvas -> NotificationBanner.INSTANCE.renderInPanel(canvas, PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
                -1.0F, -1.0F)));

        ConversationListPage conversations = new ConversationListPage(new OffscreenPageHost());
        pages.add(new Page("conversation-list", UiMotion.HOVER_MS, conversations::invalidateRows,
                canvas -> conversations.render(canvas, root, -1.0F, -1.0F, 0.0F)));

        ColorPickerOverlay picker = new ColorPickerOverlay(text -> {
        });
        SettingsColor color = new SettingsColor("golden", "atomchat.settings.color.accent", "ui",
                new int[]{0xFF3B82F6, 0xFFEF4444, 0xFF22C55E}, () -> ACCENT, value -> {
        });
        pages.add(new Page("color-picker", UiMotion.POPUP_MS, () -> picker.open(color),
                canvas -> picker.render(canvas, panelLayout.rect(), -1.0F, -1.0F, ACCENT)));

        Path source = fixtureImage();
        ImageCropper cropper = new ImageCropper(new OffscreenCropCallback());
        pages.add(new Page("image-cropper", UiMotion.MESSAGE_MS, () -> {
            cropper.open(source, false, 256.0F, 256.0F, "atomchat-avatar");
            // 解码在后台线程：等它落地，别把「加载中」那一帧拍成基准帧
            // （夹具图很小，解码是毫秒级；余量给足，确定性实证会暴露任何残余的不稳定）。
            sleep(DECODE_WAIT_MS);
        }, canvas -> cropper.render(canvas, panelLayout.rect(), -1.0F, -1.0F)));

        return pages;
    }

    /** 固定种子对话：固定文本、固定顺序、**固定时间戳**（不用 now）。 */
    private static List<ChatMessage> conversation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(seededMessage("Alice", false, 0));
        messages.add(seededMessage("a plain line from someone else", false, 1));
        messages.add(seededMessage("my own line", true, 2));
        messages.add(seededMessage(
                "a line long enough that the bubble has to wrap it onto a second row instead of "
                        + "running off the edge of the panel", false, 3));
        messages.add(new ChatMessage(Component.literal("a system notice"), false, true, null, null,
                null, "Server", null, "a system notice", SEED_EPOCH_MS + 4_000L));
        messages.add(new ChatMessage(Component.literal("the reply body"), true, false, "Bob",
                "the line being quoted", OFFSCREEN_OWN, "Offscreen", null, "the reply body",
                SEED_EPOCH_MS + 5_000L));
        messages.add(seededMessage("the same line again", false, 6)
                .withDuplicateCount(3, SEED_EPOCH_MS + 6_000L));
        return messages;
    }

    private static ChatMessage seededMessage(String text, boolean own, int index) {
        long timestamp = SEED_EPOCH_MS + index * 1_000L;
        return new ChatMessage(Component.literal(text), own, false, null, null,
                own ? OFFSCREEN_OWN : ALICE_UUID, own ? "Offscreen" : "Alice", null, text, timestamp);
    }

    /** 屏幕打开时间戳严格晚于每一条消息 → 历史消息不做入场动画（基准帧才稳定）。 */
    private static long openedAfter(List<ChatMessage> messages) {
        return messages.stream().mapToLong(ChatMessage::getTimestamp).max().orElse(0L) + 1L;
    }

    /** 夹具图：固定尺寸固定颜色，用 Skija 自己画出来，于是每次都一样。 */
    private static Path fixtureImage() {
        Path image = FIXTURE.resolve("crop-source.png");
        if (Files.isRegularFile(image)) {
            return image;
        }
        Surface surface = Surface.makeRasterN32Premul(240, 160);
        try {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint()) {
                paint.setColor(0xFF1F2937);
                canvas.drawRect(Rect.makeXYWH(0, 0, 240, 160), paint);
                paint.setColor(0xFFEF4444);
                canvas.drawRect(Rect.makeXYWH(20, 20, 80, 60), paint);
                paint.setColor(0xFF3B82F6);
                canvas.drawCircle(170, 80, 45, paint);
            }
            try (Image snapshot = surface.makeImageSnapshot();
                 Data data = snapshot.encodeToData(EncodedImageFormat.PNG)) {
                Files.createDirectories(image.getParent());
                Files.write(image, data.getBytes());
            }
        } catch (Exception e) {
            throw new IllegalStateException("写夹具图失败", e);
        } finally {
            surface.close();
        }
        return image;
    }

    /**
     * 基准帧落点：仓库根 {@code golden/<目标>/}。
     *
     * <p>仓库根与目标名由构建脚本通过系统属性给出（{@code atomchat.repoRoot} /
     * {@code atomchat.golden.target}）：测试的工作目录**不一定是工程目录** ——
     * NeoForge 的 ModDevGradle 把测试跑在 {@code build/minecraft-junit} 下，
     * 从 cwd 猜目标名会把基准帧写进 {@code golden/minecraft-junit/}（实测踩到过）。
     * 没有属性时（IDE 直跑）退回「往上找 versions/targets.json + cwd 目录名」。
     */
    private static Path goldenDir() {
        String repoRoot = System.getProperty("atomchat.repoRoot", "");
        String target = System.getProperty("atomchat.golden.target", "");
        if (!repoRoot.isEmpty() && !target.isEmpty()) {
            return Path.of(repoRoot).resolve("golden").resolve(target);
        }
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("versions/targets.json"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("找不到仓库根（往上没有 versions/targets.json）"
                    + "，也没有 atomchat.repoRoot 系统属性");
        }
        return dir.resolve("golden").resolve(Path.of("").toAbsolutePath().getFileName().toString());
    }

    /** 逐像素差：返回 [differing pixels, max channel delta]，用于不一致时的报告。 */
    private static int[] pixelDiff(byte[] expectedPng, byte[] freshPng) {
        byte[] a = decodedPixels(expectedPng);
        byte[] b = decodedPixels(freshPng);
        if (a.length != b.length) {
            return new int[]{Math.max(a.length, b.length), 255};
        }
        int differing = 0;
        int maxDelta = 0;
        for (int i = 0; i < a.length; i++) {
            int delta = Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
            if (delta != 0) {
                differing++;
                maxDelta = Math.max(maxDelta, delta);
            }
        }
        return new int[]{differing, maxDelta};
    }

    private static byte[] decodedPixels(byte[] png) {
        try (Image image = Image.makeFromEncoded(png)) {
            Surface surface = Surface.makeRasterN32Premul(image.getWidth(), image.getHeight());
            try (Bitmap bitmap = new Bitmap()) {
                surface.getCanvas().drawImage(image, 0.0F, 0.0F);
                bitmap.allocN32Pixels(image.getWidth(), image.getHeight());
                assertTrue(surface.readPixels(bitmap, 0, 0), "解码基准帧失败");
                return bitmap.readPixels();
            } finally {
                surface.close();
            }
        }
    }

    private static int drawnBytes(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "读不回光栅面");
            byte[] pixels = bitmap.readPixels();
            int nonZero = 0;
            for (byte b : pixels) {
                if (b != 0) {
                    nonZero++;
                }
            }
            return nonZero;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等动画落定时被打断", e);
        }
    }

    private static final UUID OFFSCREEN_OWN =
            UUID.nameUUIDFromBytes("golden-own".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID ALICE_UUID =
            UUID.nameUUIDFromBytes("golden-alice".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** 消息列表绘制期需要的宿主答案（与 OffscreenRenderTest 同形，但 UUID 是固定常量）。 */
    private static final class OffscreenHost implements MessageListView.Host {
        private final long openStart;

        private OffscreenHost(long openStart) {
            this.openStart = openStart;
        }

        @Override
        public UUID ownUuid() {
            return OFFSCREEN_OWN;
        }

        @Override
        public String ownName() {
            return "Offscreen";
        }

        @Override
        public String senderName(ChatMessage message) {
            return message.isOwn() ? "Offscreen" : "Alice";
        }

        @Override
        public long openStart() {
            return openStart;
        }
    }

    /** 表情面板要的合成器副作用（离屏不需要真做）。 */
    private static final class OffscreenEmojiHost implements EmojiPanel.Host {
        @Override
        public void insert(String text) {
        }

        @Override
        public void sendSticker(Path file) {
        }

        @Override
        public void pickEmoteFile() {
        }
    }

    /** 会话列表页要的导航宿主（离屏不导航）。 */
    private static final class OffscreenPageHost implements PageHost {
        @Override
        public void pushPage(AppPage page) {
        }

        @Override
        public void openWorldChat() {
        }

        @Override
        public void openPrivateChat(PlayerRef target) {
        }

        @Override
        public void openSettingsSection(SettingsSection section) {
        }

        @Override
        public void popPage() {
        }

        @Override
        public void switchRoot(AppPage root) {
        }
    }

    /** 裁剪器回调（离屏不会真的确认/取消，但接口两个方法都要实现）。 */
    private static final class OffscreenCropCallback implements ImageCropper.Callback {
        @Override
        public void onConfirm(String targetId, byte[] pngBytes) {
        }

        @Override
        public void onCancel(String targetId) {
        }
    }
}