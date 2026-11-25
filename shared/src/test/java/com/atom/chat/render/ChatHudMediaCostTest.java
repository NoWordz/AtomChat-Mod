package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.image.ChatHudGifFixture;
import com.atom.chat.image.MessageImage;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChatHudMediaCostTest {
    private static final int WIDTH = 1280, HEIGHT = 1440, COUNT = 6;
    private static final float DENSITY = 3F;
    private static final ChatHudRenderer.Viewport VIEW =
            new ChatHudRenderer.Viewport(8, 460, 400, 450, DENSITY, 1);

    @Test void measureTextAndMedia(@TempDir Path root) throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_HUD_MEDIA_BENCH")));
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "HUD A/B", 0, 0);
        try {
            assertNotEquals(0, window);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("HUD_MEDIA renderer=" + GL11C.glGetString(GL11C.GL_RENDERER));
            try (var context = DirectContext.makeGL();
                 var target = BackendRenderTarget.makeGL(WIDTH, HEIGHT, 0, 8, 0, GL11C.GL_RGBA8);
                 var gpu = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                         ColorType.RGBA_8888, ColorSpace.getSRGB());
                 var cpu = Surface.makeRasterN32Premul(WIDTH, HEIGHT)) {
                run("cpu", cpu, 0L, () -> {});
                run("gl", gpu, 1L, () -> {
                    context.flushAndSubmit(gpu);
                    GL11C.glFinish();
                });
                verifyModeRebuildsGpuInk(context, gpu);
            }
        } finally {
            HudShadow.release();
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void verifyModeRebuildsGpuInk(DirectContext context, Surface surface) {
        var config = new AtomChatConfig();
        var hud = new ChatHudRenderer((url, visible) -> null);
        var rows = List.of(message("text", 0));
        try (var bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(WIDTH, HEIGHT));
            byte[][] frames = new byte[3][];
            for (int pass = 0; pass < frames.length; pass++) {
                config.graphicsQuality = pass == 1 ? GraphicsQuality.PERFORMANCE : GraphicsQuality.ULTRA;
                surface.getCanvas().clear(0xFFE4D6B5);
                hud.paint(surface.getCanvas(), rows, VIEW, null, "Local", "Player", config, 1, 255, 1F, false, 0, (w, h) -> SkiaGraphics.createInkSurface(surface, w, h), 1L);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                frames[pass] = bitmap.readPixels();
            }
            assertFalse(Arrays.equals(frames[0], frames[1]), "the cached standard ink must not survive fast mode");
            assertArrayEquals(frames[0], frames[2], "turning fast mode off restores the prior ink");
        } finally {
            hud.clear();
        }
    }

    private static void run(String backend, Surface surface, long context, Runnable sync) throws Exception {
        var clock = new AtomicLong(1000);
        var gif = ChatHudGifFixture.frames(clock);
        var mixedGif = ChatHudGifFixture.mixedAlphaFrames(clock);
        assertNotNull(gif.apply("http://test/hud.gif", true));
        assertTrue(mixedGif.apply("http://test/hud.gif", true).transparent());
        MessageImage[] thumbnails = new MessageImage[COUNT];
        MessageImage[] cutouts = new MessageImage[COUNT];
        try {
            for (int i = 0; i < COUNT; i++) {
                try (var source = Surface.makeRasterN32Premul(256, 144); var paint = new Paint()) {
                    for (int stripe = 0; stripe < 16; stripe++) {
                        source.getCanvas().drawRect(Rect.makeXYWH(stripe * 16, 0, 16, 144),
                                paint.setColor(0xFF2D6988 + stripe * 0x00020100 + i * 12));
                    }
                    thumbnails[i] = new MessageImage(source.makeImageSnapshot(), false);
                }
                try (var source = Surface.makeRasterN32Premul(256, 144); var paint = new Paint().setColor(0xFF216F9E + i)) {
                    source.getCanvas().clear(0);
                    source.getCanvas().drawRect(Rect.makeXYWH(24, 12, 160, 120), paint);
                    cutouts[i] = new MessageImage(source.makeImageSnapshot(), true);
                }
            }
            Canvas canvas = surface.getCanvas();
            canvas.scale(DENSITY, DENSITY);
            var config = new AtomChatConfig();
            for (String scene : List.of("text", "static", "gif", "alpha-static", "alpha-gif")) {
                List<ChatMessage> messages = IntStream.range(0, COUNT).mapToObj(i -> message(scene, i)).toList();
                BiFunction<String, Boolean, MessageImage> images = switch (scene) {
                    case "gif" -> gif;
                    case "alpha-gif" -> mixedGif;
                    case "alpha-static" -> (url, visible) -> cutouts[url.charAt(url.length() - 5) - '0'];
                    default -> (url, visible) -> thumbnails[url.charAt(url.length() - 5) - '0'];
                };
                for (int repeat = 0; repeat < 3; repeat++) {
                    config.graphicsQuality = (repeat & 1) != 0 ? GraphicsQuality.PERFORMANCE : GraphicsQuality.ULTRA;
                    measure(backend, scene, "hot-" + repeat, surface, context, sync, clock, config, messages, images, 240, 300, false);
                    config.graphicsQuality = config.performanceModeEnabled() ? GraphicsQuality.ULTRA : GraphicsQuality.PERFORMANCE;
                    measure(backend, scene, "hot-" + repeat, surface, context, sync, clock, config, messages, images, 240, 300, false);
                }
                for (boolean off : new boolean[]{false, true}) {
                    config.graphicsQuality = off ? GraphicsQuality.PERFORMANCE : GraphicsQuality.ULTRA;
                    measure(backend, scene, "cold", surface, context, sync, clock, config, messages, images, 4, 30, true);
                }
            }
        } finally {
            for (MessageImage image : thumbnails) if (image != null) image.image().close();
            for (MessageImage image : cutouts) if (image != null) image.image().close();
        }
    }

    private static void measure(String backend, String scene, String phase, Surface surface, long context,
                                Runnable sync, AtomicLong clock,
                                AtomChatConfig config, List<ChatMessage> messages, BiFunction<String, Boolean, MessageImage> images,
                                int warmup, int count, boolean cold) {
        long[] paint = new long[count], flush = new long[count], combined = new long[count];
        var hud = new ChatHudRenderer(images);
        Canvas canvas = surface.getCanvas();
        ChatHudRasterCache.SurfaceFactory inkSurface = context == 0 ? null
                : (w, h) -> SkiaGraphics.createInkSurface(surface, w, h);
        try {
            for (int i = -warmup; i < count; i++) {
                if (cold) {
                    hud.clear();
                    HudShadow.release();
                }
                canvas.clear(0xFF957E61);
                sync.run();
                long now = clock.addAndGet(16);
                long start = System.nanoTime();
                hud.paint(canvas, messages, VIEW, null, "Local", "Player", config, COUNT, 230, 1F, false, now,
                        inkSurface, context);
                long painted = System.nanoTime();
                sync.run();
                long end = System.nanoTime();
                if (i >= 0) {
                    paint[i] = painted - start;
                    flush[i] = end - painted;
                    combined[i] = end - start;
                }
            }
        } finally {
            hud.clear();
        }
        System.out.printf(Locale.ROOT, "HUD_MEDIA %s %s %s %s paint=%s flushFence=%s combined=%s%n",
                backend, scene, config.performanceModeEnabled() ? "off" : "on", phase, quantiles(paint), quantiles(flush), quantiles(combined));
    }

    private static String quantiles(long[] samples) {
        Arrays.sort(samples);
        return String.format(Locale.ROOT, "%.4f/%.4f", samples[samples.length / 2] / 1e6,
                samples[samples.length * 95 / 100] / 1e6);
    }

    private static ChatMessage message(String scene, int i) {
        String value = switch (scene) {
            case "text" -> "Representative rich chat text " + i;
            case "static", "alpha-static" -> "[[CICode,url=http://test/hud" + i + ".png,name=image,width=256,height=144]]";
            case "gif", "alpha-gif" -> "[[CICode,url=http://test/hud.gif,name=image]]";
            default -> throw new IllegalArgumentException(scene);
        };
        var name = RichText.literal("Styled sender " + i).mapStyles(style -> style.withColor(0xFF80FF));
        var body = RichText.literal(value);
        return new ChatMessage(body.toText(), false, false, null, null, null, name.getString(), "",
                body.getString(), name, body);
    }
}
