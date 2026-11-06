package com.atom.chat.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AvatarReadbackWiringTest {
    @Test
    void bothMappingsUseTheBoundedTextureReader() throws Exception {
        Path root = repositoryRoot();
        for (String path : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/image/AvatarRenderer.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/image/AvatarRenderer.java")) {
            String source = Files.readString(root.resolve(path));
            assertFalse(source.contains("glGetTexImage("),
                    path + ": fixed-size client buffers let HD skins overwrite native memory");
            assertTrue(source.contains("SkinTextureReader.readAsync(glId)"), path);
        }
    }

    @Test
    void theTextureReaderIsTheOnlyDirectReadbackCall() throws Exception {
        Path root = repositoryRoot();
        List<String> callers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.contains("/src/main/java/") || !relative.endsWith(".java")) continue;
                if (relative.startsWith("build/") || relative.startsWith(".work/")) continue;
                if (Files.readString(file).contains("glGetTexImage(")) callers.add(relative);
            }
        }
        assertEquals(List.of("shared/src/main/java/com/atom/chat/image/SkinTextureReader.java"), callers);
    }

    private static Path repositoryRoot() throws IOException {
        String configured = System.getProperty("atomchat.repoRoot");
        Path root = configured == null ? Path.of("").toAbsolutePath() : Path.of(configured);
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        assertNotNull(root, "repository root not found");
        return root;
    }

    /**
     * Exercises the production readback against a real driver. The hidden window
     * keeps this independent of Minecraft, while the assertions still consume the
     * bytes the GL implementation wrote. Linux CI has no display and skips it.
     */
    @Nested
    class RealGlReadback {
        private static final int GRID = 64;
        private long window;
        private boolean glfwInitialized;
        private final List<Integer> textures = new ArrayList<>();
        private final List<Integer> buffers = new ArrayList<>();

        @BeforeEach
        void openHiddenContext() {
            assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"),
                    "real OpenGL readback requires a Windows display context");
            glfwInitialized = GLFW.glfwInit();
            assertTrue(glfwInitialized, "GLFW initialization failed");
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            window = GLFW.glfwCreateWindow(32, 32, "AtomChat skin readback test", 0, 0);
            assertNotEquals(0L, window, "could not create hidden OpenGL window");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
        }

        @AfterEach
        void closeHiddenContext() {
            if (window != 0L) {
                SkinTextureReader.release();
                for (int buffer : buffers) GL15C.glDeleteBuffers(buffer);
                for (int texture : textures) GL11C.glDeleteTextures(texture);
                GLFW.glfwMakeContextCurrent(0);
                GLFW.glfwDestroyWindow(window);
                window = 0L;
            }
            if (glfwInitialized) {
                GLFW.glfwTerminate();
                glfwInitialized = false;
            }
        }

        @Test
        void pboReadIsIssuedBeforeWaitingAndKeepsTheTexturePathAsynchronous() {
            int texture = createTexture(128, 128);
            int previous = createTexture(64, 64);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);

            SkinTextureReader.Result started = SkinTextureReader.readAsync(texture);
            assertEquals(SkinTextureReader.State.PENDING, started.state());
            assertNull(started.pixels());

            GL11C.glFinish();
            SkinTextureReader.Result ready = SkinTextureReader.readAsync(texture);
            assertEquals(SkinTextureReader.State.READY, ready.state());
            assertNotNull(ready.pixels());
            assertPattern(ready.pixels(), 8, 8, 2, "asynchronous face");
            assertEquals(previous, GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
            assertGlClean();
        }

        @Test
        void fullQueueWaitsForCapacityInsteadOfRestartingTheOldestRead() {
            int[] skins = new int[9];
            for (int i = 0; i < skins.length; i++) skins[i] = createTexture(128, 128);
            int previous = createTexture(64, 64);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);

            for (int skin : skins) {
                assertEquals(SkinTextureReader.State.PENDING, SkinTextureReader.readAsync(skin).state());
            }
            GL11C.glFinish();

            SkinTextureReader.Result ninthWhileFull = SkinTextureReader.readAsync(skins[8]);
            assertEquals(SkinTextureReader.State.PENDING, ninthWhileFull.state());
            GL11C.glFinish();

            assertEquals(SkinTextureReader.State.READY, SkinTextureReader.readAsync(skins[0]).state(),
                    "a full queue must not evict the first outstanding read");
            assertEquals(SkinTextureReader.State.PENDING, SkinTextureReader.readAsync(skins[8]).state(),
                    "the ninth read starts only after a slot is delivered");
            GL11C.glFinish();
            assertEquals(SkinTextureReader.State.READY, SkinTextureReader.readAsync(skins[8]).state(),
                    "the ninth skin must not starve behind capacity churn");
            assertEquals(previous, GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
            assertGlClean();
        }

        @Test
        void abandonedFullQueueExpiresEvenWhenItsOwnersStopBeingDrawn() throws Exception {
            int[] abandoned = new int[8];
            for (int i = 0; i < abandoned.length; i++) abandoned[i] = createTexture(128, 128);
            for (int skin : abandoned) {
                assertEquals(SkinTextureReader.State.PENDING, SkinTextureReader.readAsync(skin).state());
            }

            Thread.sleep(2_100L);
            int next = createTexture(128, 128);
            assertEquals(SkinTextureReader.State.PENDING, SkinTextureReader.readAsync(next).state(),
                    "the new texture starts after stale owners are reclaimed");
            GL11C.glFinish();
            assertEquals(SkinTextureReader.State.READY, SkinTextureReader.readAsync(next).state());
            assertGlClean();
        }

        @Test
        void normalizesStandardAndHdSkinsAtFaceHatAndAlphaCoordinates() {
            int[][] geometries = {{64, 64}, {64, 32}, {128, 128}, {1024, 512}};
            for (int[] geometry : geometries) {
                int width = geometry[0];
                int height = geometry[1];
                int texture = createTexture(width, height);
                int previous = createTexture(64, 64);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);

                ByteBuffer grid = SkinTextureReader.read(texture);
                assertNotNull(grid, width + "x" + height);
                assertEquals(GRID * GRID * 4, grid.capacity());
                int scale = width / GRID;
                assertPattern(grid, 8, 8, scale, width + "x" + height + " face");
                assertPattern(grid, 40, 8, scale, width + "x" + height + " hat");
                assertPattern(grid, 0, 0, scale, width + "x" + height + " transparent corner");
                assertEquals(0, Byte.toUnsignedInt(grid.get((0 * GRID + 0) * 4 + 3)));
                assertPattern(grid, 63, height / scale - 1, scale,
                        width + "x" + height + " last normalized row");
                assertZeroRows(grid, height / scale);
                assertEquals(previous, GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
                assertGlClean();
            }
        }

        @Test
        void rejectsMalformedGeometryBeforeAnyReadback() {
            int[][] geometries = {{0, 0}, {48, 64}, {96, 96}, {128, 96}};
            for (int[] geometry : geometries) {
                int texture = createTexture(geometry[0], geometry[1]);
                int previous = createTexture(64, 64);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
                assertNull(SkinTextureReader.read(texture), geometry[0] + "x" + geometry[1]);
                assertEquals(previous, GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
                assertGlClean();
            }
        }

        @Test
        void restoresForeignPackStatePboAndTextureBinding() {
            int texture = createTexture(128, 128);
            int previous = createTexture(64, 64);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            int pbo = GL15C.glGenBuffers();
            buffers.add(pbo);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbo);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, 4096, GL15C.GL_STREAM_READ);
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 8);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 37);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 5);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 3);
            assertGlClean();

            ByteBuffer grid = SkinTextureReader.read(texture);
            assertNotNull(grid);
            assertPattern(grid, 8, 8, 2, "face with polluted pack state");
            assertEquals(8, GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT));
            assertEquals(37, GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH));
            assertEquals(5, GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS));
            assertEquals(3, GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS));
            assertEquals(pbo, GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(previous, GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
            assertGlClean();
        }

        private int createTexture(int width, int height) {
            int texture = GL11C.glGenTextures();
            textures.add(texture);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int channel = 0; channel < 4; channel++) {
                        pixels.put((byte) pattern(x, y, channel));
                    }
                }
            }
            pixels.flip();
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, width, height, 0,
                    GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            assertGlClean();
            return texture;
        }

        private void assertPattern(ByteBuffer grid, int x, int y, int scale, String label) {
            int sourceX = x * scale;
            int sourceY = y * scale;
            int offset = (y * GRID + x) * 4;
            for (int channel = 0; channel < 4; channel++) {
                assertEquals(pattern(sourceX, sourceY, channel), Byte.toUnsignedInt(grid.get(offset + channel)),
                        label + " channel " + channel);
            }
        }

        private void assertZeroRows(ByteBuffer grid, int firstEmptyRow) {
            for (int i = firstEmptyRow * GRID * 4; i < grid.capacity(); i++) {
                assertEquals(0, grid.get(i), "normalized grid must not retain stale rows at byte " + i);
            }
        }

        private void assertGlClean() {
            int error = GL11C.glGetError();
            assertEquals(GL11C.GL_NO_ERROR, error, "unexpected OpenGL error 0x" + Integer.toHexString(error));
        }
    }

    private static int pattern(int x, int y, int channel) {
        return switch (channel) {
            case 0 -> (x * 3 + y * 5 + 17) & 0xff;
            case 1 -> (x * 7 + y * 11 + 29) & 0xff;
            case 2 -> (x * 13 + y * 17 + 43) & 0xff;
            default -> (x * 37 + y * 61) & 0xff;
        };
    }
}
