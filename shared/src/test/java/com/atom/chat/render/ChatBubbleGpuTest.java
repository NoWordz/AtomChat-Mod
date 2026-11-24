package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import com.atom.chat.util.GlTestEnvironment;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChatBubbleGpuTest {
    @Test void groupedCircularFillsAndImageClipsMatchTheNativeAreaReference() throws Exception {
        GlTestEnvironment.requireWindowContext();
        int width = 190, height = 100, samples = 16;
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(width, height, "AtomChat circular bubble verification", 0, 0);
        assertNotEquals(0L, window);
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            try (var context = DirectContext.makeGL();
                 var target = BackendRenderTarget.makeGL(width, height, 0, 8, 0, GL11C.GL_RGBA8);
                 var host = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                         ColorType.RGBA_8888, ColorSpace.getSRGB());
                 var actual = host.makeSurface(ImageInfo.makeN32Premul(width, height));
                 var reference = Surface.makeRasterN32Premul(width * samples, height * samples);
                 var source = Surface.makeRasterN32Premul(120, 50);
                 var low = new Bitmap(); var high = new Bitmap();
                 var paint = new Paint().setAntiAlias(true).setColor(-1)) {
                low.allocN32Pixels(width, height);
                high.allocN32Pixels(width * samples, height * samples);
                source.getCanvas().clear(-1);
                var output = new StringBuilder("group,own,phase,edge_alpha_mae,image_alpha_max_difference\n");
                try (var image = source.makeImageSnapshot()) {
                    for (var group : ChatBubble.Group.values()) for (boolean own : new boolean[]{false, true})
                    for (float phase : new float[]{0F, .5F}) {
                        var canvas = actual.getCanvas();
                        canvas.clear(0);
                        int save = canvas.save();
                        canvas.scale(1.25F, 1.25F);
                        ChatBubble.draw(canvas, 10.25F + phase, 10.5F, 120, 50, BubbleStyle.CORNER, own, -1, group);
                        canvas.restoreToCount(save);
                        context.flushAndSubmit(actual);
                        assertTrue(actual.readPixels(low, 0, 0));
                        byte[] filled = low.readPixels();
                        reference.getCanvas().clear(0);
                        save = reference.getCanvas().save();
                        reference.getCanvas().scale(1.25F * samples, 1.25F * samples);
                        reference.getCanvas().drawRRect(ChatBubble.outline(10.25F + phase, 10.5F, 120, 50,
                                BubbleStyle.CORNER, own, 1, group), paint);
                        reference.getCanvas().restoreToCount(save);
                        assertTrue(reference.readPixels(high, 0, 0));
                        byte[] sampled = high.readPixels();
                        double error = 0;
                        int edges = 0;
                        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                            int sum = 0;
                            for (int dy = 0; dy < samples; dy++) for (int dx = 0; dx < samples; dx++) {
                                sum += sampled[((y * samples + dy) * width * samples + x * samples + dx) * 4 + 3] & 255;
                            }
                            float expected = sum / (float)(samples * samples);
                            int alpha = filled[(y * width + x) * 4 + 3] & 255;
                            if (expected != alpha || alpha > 0 && alpha < 255) {
                                edges++;
                                error += Math.abs(expected - alpha);
                            }
                        }
                        assertTrue(edges > 200);
                        double mae = error / edges;
                        assertTrue(mae < 3, group + "/" + own + ": edge alpha MAE=" + mae);
                        canvas.clear(0);
                        save = canvas.save();
                        canvas.scale(1.25F, 1.25F);
                        ChatBubble.drawImage(canvas, image, 10.25F + phase, 10.5F, 120, 50, BubbleStyle.CORNER, own, group);
                        canvas.restoreToCount(save);
                        context.flushAndSubmit(actual);
                        assertTrue(actual.readPixels(low, 0, 0));
                        byte[] clipped = low.readPixels();
                        int maximum = 0;
                        for (int i = 3; i < filled.length; i += 4) maximum = Math.max(maximum, Math.abs((filled[i] & 255) - (clipped[i] & 255)));
                        assertTrue(maximum <= 1, group + "/" + own + ": image/fill alpha difference=" + maximum);
                        output.append(group).append(',').append(own).append(',').append(phase).append(',')
                                .append(mae).append(',').append(maximum).append('\n');
                    }
                }
                Path path = Path.of(System.getProperty("atomchat.repoRoot", "../.."), "build/v096/gpu",
                        System.getProperty("atomchat.golden.target", "local"), "circular-bubbles.csv");
                Files.createDirectories(path.getParent());
                Files.writeString(path, output.toString());
                System.out.println("Circular bubble GPU: " + GL11C.glGetString(GL11C.GL_RENDERER));
            }
        } finally {
            GLFW.glfwMakeContextCurrent(0);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
