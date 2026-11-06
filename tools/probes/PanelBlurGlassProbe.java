package com.atom.chat.probe;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compiles the real shaders and checks RGB and mask alpha on an offscreen context. */
public final class PanelBlurGlassProbe {
    private static final int W = 320;
    private static final int H = 200;

    private static int program;
    private static int vao;
    private static int texBright;
    private static int texDark;

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath();
        while (repoRoot != null && !repoRoot.resolve("shared/PLATFORM-SEAMS.md").toFile().exists()) {
            repoRoot = repoRoot.getParent();
        }
        if (repoRoot == null) throw new AssertionError("repo root not found");
        Path fshPath = args.length > 1 ? Path.of(args[1]) :
                repoRoot.resolve("platforms/1.21.1-neoforge/src/main/resources/assets/atomchat/shaders/core/atomchat_panel_blur.fsh");
        Path vshPath = args.length > 2 ? Path.of(args[2]) :
                repoRoot.resolve("platforms/1.21.1-neoforge/src/main/resources/assets/atomchat/shaders/core/atomchat_panel_blur.vsh");
        String fsh = Files.readString(fshPath);
        String vsh = Files.readString(vshPath);

        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
        long window = GLFW.glfwCreateWindow(W, H, "AtomChat glass probe", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL 3.2 core window creation failed");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        program = link(vsh, fsh);
        int maxLumaLoc = GL20C.glGetUniformLocation(program, "u_MaxLuma");

        // Full-screen quad as two triangles in NDC (identity matrices make
        // clip space the coordinate space, so pixel coords would clip away):
        // Position(3), UV0(2), Color(4) — the vertex format the core shader
        // declares (POSITION_TEX_COLOR).
        float[] verts = {
                -1, -1, 0, 0, 0, 1, 1, 1, 1,
                -1,  1, 0, 0, 1, 1, 1, 1, 1,
                 1,  1, 0, 1, 1, 1, 1, 1, 1,
                -1, -1, 0, 0, 0, 1, 1, 1, 1,
                 1,  1, 0, 1, 1, 1, 1, 1, 1,
                 1, -1, 0, 1, 0, 1, 1, 1, 1,
        };
        vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(vao);
        int vbo = GL30C.glGenBuffers();
        GL20C.glBindBuffer(GL20C.GL_ARRAY_BUFFER, vbo);
        GL20C.glBufferData(GL20C.GL_ARRAY_BUFFER, verts, GL20C.GL_STATIC_DRAW);
        for (int i = 0; i < 3; i++) {
            GL20C.glEnableVertexAttribArray(i);
        }
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 9 * 4, 0L);
        GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, 9 * 4, 12L);
        GL20C.glVertexAttribPointer(2, 4, GL11C.GL_FLOAT, false, 9 * 4, 20L);

        texBright = constantTexture(0.92F, 0.92F, 0.92F);
        texDark = constantTexture(0.12F, 0.12F, 0.12F);
        int texColor = constantTexture(0.90F, 0.60F, 0.30F);

        GL11C.glDisable(GL11C.GL_BLEND);
        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL20C.glUseProgram(program);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "Sampler0"), 0);
        // Raw GL has no json defaults (those are MC's ShaderInstance's doing):
        // an uninitialized uniform reads 0, so ColorModulator must be set
        // explicitly to the identity the renderer's draw path uses.
        GL20C.glUniform4f(GL20C.glGetUniformLocation(program, "ColorModulator"), 1.0F, 1.0F, 1.0F, 1.0F);
        // Same rule for the matrices: raw GL zero-initializes them, so the
        // quad would collapse to a point; set identities (the renderer's
        // ModelViewMat-equivalent is baked into Position here).
        FloatBuffer identity = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        identity.put(new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1,
        }).flip();
        GL20C.glUniformMatrix4fv(GL20C.glGetUniformLocation(program, "ModelViewMat"), false, identity);
        GL20C.glUniformMatrix4fv(GL20C.glGetUniformLocation(program, "ProjMat"), false, identity);
        // u_Rect = center.xy, half-size.zw in Position's space (NDC here).
        GL20C.glUniform4f(GL20C.glGetUniformLocation(program, "u_Rect"), 0.0F, 0.0F, 1.0F, 1.0F);
        GL20C.glUniform1f(GL20C.glGetUniformLocation(program, "u_Radius"), 0.08F);
        GL20C.glUniform1f(GL20C.glGetUniformLocation(program, "u_FlipV"), 1.0F);
        GL11C.glClearColor(0F, 0F, 0F, 0F);
        GL11C.glViewport(0, 0, W, H);
        // Establish a clean error queue before the first measured frame.
        GL11C.glGetError();

        // Old shader is exercised with its actual default ceiling when present.
        checkColor("bright", draw(maxLumaLoc, texBright, 0.35F), 0.92F, 0.92F, 0.92F);
        checkColor("dark", draw(maxLumaLoc, texDark, 0.35F), 0.12F, 0.12F, 0.12F);
        checkColor("color", draw(maxLumaLoc, texColor, 0.35F), 0.90F, 0.60F, 0.30F);
        GL20C.glUniform4f(GL20C.glGetUniformLocation(program, "ColorModulator"), 1.0F, 1.0F, 1.0F, 0.5F);
        float[] faded = draw(maxLumaLoc, texBright, 0.35F);
        expect("fade alpha ignores texture alpha", faded[3], 0.5F, 0.005F);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("PANEL_GLASS_PROBE_PASS fsh=" + fshPath);
    }

    private static void checkColor(String label, float[] actual, float r, float g, float b) {
        expect(label + " red", actual[0], r, 0.02F);
        expect(label + " green", actual[1], g, 0.02F);
        expect(label + " blue", actual[2], b, 0.02F);
        expectAlpha(label + " alpha", actual[3], 1.0F);
    }

    /** Draws one frame and reads back the center pixel as RGBA in 0..1. */
    private static float[] draw(int maxLumaLoc, int texture, float maxLuma) {
        GL20C.glUniform1f(maxLumaLoc, maxLuma);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 6);
        float[] px = new float[4];
        ByteBuffer buf = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        GL11C.glReadPixels(W / 2, H / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_FLOAT, buf);
        buf.asFloatBuffer().get(px);
        // Check at the frame tail, after the readbacks: GL errors queue
        // globally, so a check placed mid-frame only attributes the previous
        // frame's leftover.
        int glErr = GL11C.glGetError();
        if (glErr != 0) {
            throw new AssertionError("GL error 0x" + Integer.toHexString(glErr) + " at frame tail");
        }
        if (System.getenv("PANEL_GLASS_DEBUG") != null) {
            ByteBuffer row = ByteBuffer.allocateDirect(W * 4).order(ByteOrder.nativeOrder());
            GL11C.glReadPixels(0, H / 2, W, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, row);
            StringBuilder sb = new StringBuilder("row y=H/2:");
            for (int x = 0; x < W; x += 32) {
                sb.append(String.format(" %d:[%02x %02x %02x %02x]", x,
                        row.get(x * 4) & 0xFF, row.get(x * 4 + 1) & 0xFF,
                        row.get(x * 4 + 2) & 0xFF, row.get(x * 4 + 3) & 0xFF));
            }
            System.out.println(sb);
            System.out.println("attribs: Position=" + GL20C.glGetVertexAttribi(0, GL20C.GL_VERTEX_ATTRIB_ARRAY_ENABLED)
                    + " UV0=" + GL20C.glGetVertexAttribi(1, GL20C.GL_VERTEX_ATTRIB_ARRAY_ENABLED)
                    + " Color=" + GL20C.glGetVertexAttribi(2, GL20C.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            System.out.println("alphaBits=" + GL11C.glGetInteger(0x0D55)
                    + " drawBuffer=" + GL11C.glGetInteger(GL11C.GL_DRAW_BUFFER));
        }
        return px;
    }

    /** A 4x4 RGBA8 texture of one constant color, LINEAR/CLAMP like the blur result. */
    private static int constantTexture(float r, float g, float b) {
        int tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL30C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL30C.GL_CLAMP_TO_EDGE);
        ByteBuffer px = ByteBuffer.allocateDirect(16 * 4 * 4).order(ByteOrder.nativeOrder());
        FloatBuffer f = px.asFloatBuffer();
        for (int i = 0; i < 16; i++) {
            f.put(r).put(g).put(b).put(0.2F);
        }
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, 4, 4, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, px);
        return tex;
    }

    private static int compile(int type, String source, String name) {
        int sh = GL20C.glCreateShader(type);
        GL20C.glShaderSource(sh, source);
        GL20C.glCompileShader(sh);
        if (GL20C.glGetShaderi(sh, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            throw new AssertionError(name + " failed to compile:\n" + GL20C.glGetShaderInfoLog(sh));
        }
        return sh;
    }

    private static int link(String vsh, String fsh) {
        int vs = compile(GL20C.GL_VERTEX_SHADER, vsh, "vsh");
        int fs = compile(GL20C.GL_FRAGMENT_SHADER, fsh, "fsh");
        int p = GL20C.glCreateProgram();
        GL20C.glAttachShader(p, vs);
        GL20C.glAttachShader(p, fs);
        GL20C.glBindAttribLocation(p, 0, "Position");
        GL20C.glBindAttribLocation(p, 1, "UV0");
        GL20C.glBindAttribLocation(p, 2, "Color");
        GL20C.glLinkProgram(p);
        if (GL20C.glGetProgrami(p, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new AssertionError("program failed to link:\n" + GL20C.glGetProgramInfoLog(p));
        }
        return p;
    }

    private static void expect(String what, float actual, float expected, float tol) {
        if (Math.abs(actual - expected) > tol) {
            throw new AssertionError(what + ": expected ~" + expected + ", got " + actual);
        }
        System.out.println("  ok: " + what + " = " + actual);
    }

    private static void expectAlpha(String what, float actual, float expected) {
        if (Math.abs(actual - expected) > 0.5F / 255F) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
        System.out.println("  ok: " + what + " = " + actual);
    }
}
