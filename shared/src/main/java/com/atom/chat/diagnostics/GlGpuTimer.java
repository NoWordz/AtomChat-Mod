package com.atom.chat.diagnostics;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Production {@link GpuTimerQuery.Gl} backed by LWJGL OpenGL 3.3 timer
 * queries. One independent query object per frame; the ring polls results on a
 * later frame only when GL_QUERY_RESULT_AVAILABLE is true.
 *
 * <p>Safety contract for the render loop (P1 red lines):
 * <ul>
 *   <li>No {@code glFinish} and no blocking result read: {@link #resultNanos}
 *       is reached only after {@link #resultAvailable} returned true, which the
 *       GL spec guarantees is non-blocking.</li>
 *   <li>Other mods are avoided: {@link #otherQueryActive()} inspects
 *       {@code GL_CURRENT_QUERY} on GL_TIME_ELAPSED before we begin our own.</li>
 *   <li>Every entry point degrades silently. When no GL context/capabilities
 *       are present (e.g. plain JVM unit tests, or a lost context), the cheap
 *       guards return false and the frame is skipped, never stalled. GL calls
 *       that still fail are turned into skipped/dropped/expired counts by
 *       {@link GpuTimerQuery}'s own try/catch, never into an exception that can
 *       escape into the frame loop.</li>
 * </ul>
 *
 * <p>This measures the panel GL interval (the frame's GL commands, which
 * include the raw-GL background blur pre-pass), not the whole game frame and
 * not CPU submission time. It is a diagnostic only; numbers are not game FPS.
 */
public final class GlGpuTimer implements GpuTimerQuery.Gl {

    private static final int TARGET = GL33C.GL_TIME_ELAPSED;

    private static boolean live() {
        try {
            GLCapabilities caps = GL.getCapabilities();
            return caps != null && caps.OpenGL33;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean timerQuerySupported() {
        return live();
    }

    @Override
    public boolean contextStillValid() {
        return live();
    }

    @Override
    public boolean otherQueryActive() {
        if (!live()) return true; // no context -> conservatively skip this frame
        try {
            return GL15C.glGetQueryi(TARGET, GL15C.GL_CURRENT_QUERY) != 0;
        } catch (Throwable ignored) {
            return true; // cannot prove no conflict -> skip, do not collide
        }
    }

    @Override
    public int createQuery() {
        return GL15C.glGenQueries();
    }

    @Override
    public void deleteQuery(int query) {
        try { GL15C.glDeleteQueries(query); } catch (Throwable ignored) { }
    }

    @Override
    public void beginQuery(int query) {
        GL15C.glBeginQuery(TARGET, query);
    }

    @Override
    public void endQuery() {
        GL15C.glEndQuery(TARGET);
    }

    @Override
    public boolean resultAvailable(int query) {
        return GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    @Override
    public long resultNanos(int query) {
        // GL_QUERY_RESULT is nanoseconds. Reached only after resultAvailable()==true,
        // so this read never blocks the frame loop.
        return GL33C.glGetQueryObjectui64(query, GL15C.GL_QUERY_RESULT);
    }
}
