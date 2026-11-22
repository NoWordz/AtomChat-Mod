package com.atom.chat.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.atom.chat.ui.UiLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PanelBlurReloadTest {
    @Test void resourceReloadRetiresBothLinkedPrograms() throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_BLUR_RELOAD_TEST")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(64, 64, "AtomChat shader reload test", 0, 0);
        try {
            assertNotEquals(0, window);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            ResourceFactory factory = shaderResources();
            Field roundedField = shaderField("roundedShader");
            Field kawaseField = shaderField("kawaseShader");
            for (int reload = 0; reload < 20; reload++) {
                var rounded = new ShaderProgram(factory, "atomchat_panel_blur", VertexFormats.POSITION_TEXTURE_COLOR);
                var kawase = new ShaderProgram(factory, "atomchat_kawase_blur", VertexFormats.POSITION);
                try {
                    roundedField.set(null, rounded);
                    kawaseField.set(null, kawase);
                    assertTrue(GL20C.glIsProgram(rounded.getGlRef()));
                    assertTrue(GL20C.glIsProgram(kawase.getGlRef()));
                    PanelBlurRenderer.resetShader();
                    assertFalse(GL20C.glIsProgram(rounded.getGlRef()), "rounded program survived reload " + reload);
                    assertFalse(GL20C.glIsProgram(kawase.getGlRef()), "Kawase program survived reload " + reload);
                    assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
                } finally {
                    if (GL20C.glIsProgram(rounded.getGlRef())) rounded.close();
                    if (GL20C.glIsProgram(kawase.getGlRef())) kawase.close();
                    roundedField.set(null, null);
                    kawaseField.set(null, null);
                }
            }
        } finally {
            GLFW.glfwMakeContextCurrent(0);
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    @Test void hudMasksCompositeDistinctScreenSamplesAndKeepTheFramebufferAlpha() throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_BLUR_RELOAD_TEST")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(64, 64, "AtomChat HUD blur masks", 0, 0);
        try {
            assertNotEquals(0, window);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            var field = shaderField("roundedShader");
            Tessellator.initialize();
            int texture = GL11C.glGenTextures();
            RenderSystem.backupProjectionMatrix();
            try (var client = installWindowForShader();
                 var shader = new ShaderProgram(shaderResources(), "atomchat_panel_blur", VertexFormats.POSITION_TEXTURE_COLOR)) {
                field.set(null, shader);
                var samples = BufferUtils.createByteBuffer(64 * 64 * 4);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) samples.put((byte) (4 * x)).put((byte) (4 * y)).put((byte) 0).put((byte) 0);
                }
                samples.flip();
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, 64, 64, 0,
                        GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, samples);
                GL11C.glViewport(0, 0, 64, 64);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, 64F, 64F, 0F, -1F, 1F), VertexSorter.BY_Z);
                GL11C.glClearColor(0F, 0F, 1F, .25F);
                GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                var masks = List.of(new BlurMask(new UiLayout.Rect(8, 4, 40, 12), 5, 1F),
                        new BlurMask(new UiLayout.Rect(12, 23, 40, 12), 5, .5F),
                        new BlurMask(new UiLayout.Rect(8, 44, 40, 12), 5, 1F));
                GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_REVERSE_SUBTRACT, GL14C.GL_FUNC_SUBTRACT);
                GL14C.glBlendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ZERO, GL11C.GL_ONE, GL11C.GL_ZERO);
                assertTrue(PanelBlurRenderer.drawMaskedQuads(new Matrix4f(), masks, 1, 8, 56, 64, 64, texture));
                assertEquals(GL14C.GL_FUNC_REVERSE_SUBTRACT, GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB));
                assertEquals(GL14C.GL_FUNC_SUBTRACT, GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA));
                assertEquals(GL11C.GL_ONE, GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB));
                assertEquals(GL11C.GL_ZERO, GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB));
                assertArrayEquals(new int[]{48, 180, 0, 64}, readPixel(20, 10));
                assertArrayEquals(new int[]{48, 28, 0, 64}, readPixel(20, 48));
                int[] fading = readPixel(20, 28);
                assertEquals(24, fading[0], 1);
                assertEquals(54, fading[1], 1);
                assertEquals(128, fading[2], 1);
                assertEquals(64, fading[3]);
                assertArrayEquals(new int[]{0, 0, 255, 64}, readPixel(20, 19), "the gap is untouched");
                assertArrayEquals(new int[]{0, 0, 255, 64}, readPixel(8, 4), "the rounded corner is untouched");
                GL11C.glClearColor(0F, 0F, 1F, 0F);
                GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                var fractional = List.of(new BlurMask(new UiLayout.Rect(10.75F, 10.75F, 40F, 26F), 0F, 1F));
                assertTrue(PanelBlurRenderer.drawMaskedQuads(new Matrix4f(), fractional, 1, 0, 64, 64, 64, texture));
                assertEquals(191, readPixel(10, 20)[2], 1, "the outside-center pixel retains its quarter coverage");
                assertEquals(239, readPixel(10, 10)[2], 1, "both square corner axes contribute coverage");
                assertEquals(0, readPixel(10, 20)[3]);
                assertEquals(0, readPixel(20, 20)[3]);

                var roundRect = new UiLayout.Rect(11.02F, 11.02F, 44F, 44F);
                GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                assertTrue(PanelBlurRenderer.drawMaskedQuads(new Matrix4f(),
                        List.of(new BlurMask(roundRect, -20F, 1F)), 1, 0, 64, 64, 64, texture));
                assertEquals(128, readPixel(15, 15)[2], 3, "sample on the continuous diagonal");
                assertEquals(0, readPixel(15, 15)[3]);

                GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                var narrowRadius = new UiLayout.Rect(10.9F, 10F, 36F, 36F);
                assertTrue(PanelBlurRenderer.drawMaskedQuads(new Matrix4f(),
                        List.of(new BlurMask(narrowRadius, -3F, 1F)), 1, 0, 64, 64, 64, texture));
                assertEquals(230, readPixel(10, 25)[2], 1, "a straight edge retains exactly one tenth of the pixel");
                assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
            } finally {
                field.set(null, null);
                BufferRenderer.reset();
                RenderSystem.restoreProjectionMatrix();
                GL11C.glDeleteTextures(texture);
            }
        } finally {
            GLFW.glfwMakeContextCurrent(0);
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static AutoCloseable installWindowForShader() throws Exception {
        // VertexBuffer asks for the current window to populate shader uniforms.
        // Install only that dependency so this test uses the real draw path without starting Minecraft.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var window = (Window) unsafe.allocateInstance(Window.class);
        window.setFramebufferWidth(64);
        window.setFramebufferHeight(64);
        var client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
        Field windowField = MinecraftClient.class.getDeclaredField("window");
        windowField.setAccessible(true);
        windowField.set(client, window);
        Field clientField = MinecraftClient.class.getDeclaredField("instance");
        clientField.setAccessible(true);
        Object previous = clientField.get(null);
        clientField.set(null, client);
        return () -> clientField.set(null, previous);
    }

    private static int[] readPixel(int x, int y) {
        var pixel = BufferUtils.createByteBuffer(4);
        GL11C.glReadPixels(x, 63 - y, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
        return new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255, pixel.get(3) & 255};
    }

    private ResourceFactory shaderResources() {
        return id -> {
            String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
            if (getClass().getResource(path) == null) return Optional.empty();
            return Optional.of(new Resource(null, () -> getClass().getResourceAsStream(path)) {
                @Override public String getPackId() { return "atomchat-test"; }
            });
        };
    }

    private static Field shaderField(String name) throws Exception {
        Field field = PanelBlurRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
