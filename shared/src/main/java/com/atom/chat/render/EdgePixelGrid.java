package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;

/** Axis-aligned pixel grid of a direct draw or a recording with a known destination. */
public record EdgePixelGrid(float sx, float sy, float x, float y) {
    private record Recording(Canvas canvas, EdgePixelGrid destination) { }
    private static final ThreadLocal<Recording> RECORDING = new ThreadLocal<>();

    public static void record(Canvas recording, Canvas destination, Runnable content) {
        Recording previous = RECORDING.get();
        EdgePixelGrid grid = from(destination, false);
        if (grid == null) {
            content.run();
            return;
        }
        RECORDING.set(new Recording(recording, grid));
        try {
            content.run();
        } finally {
            if (previous == null) RECORDING.remove();
            else RECORDING.set(previous);
        }
    }

    static EdgePixelGrid from(Canvas canvas, boolean rasterOnly) {
        var m = canvas.getLocalToDeviceAsMatrix33()._mat;
        if (!Float.isFinite(m[0]) || !Float.isFinite(m[4]) || m[0] <= 0F || m[4] <= 0F
                || m[1] != 0F || m[3] != 0F || m[6] != 0F || m[7] != 0F || m[8] != 1F) return null;
        Recording recording = RECORDING.get();
        if (recording != null && recording.canvas() == canvas) {
            if (rasterOnly) return null;
            EdgePixelGrid grid = recording.destination();
            return new EdgePixelGrid(grid.sx() * m[0], grid.sy() * m[4],
                    grid.sx() * m[2] + grid.x(), grid.sy() * m[5] + grid.y());
        }
        try (var surface = canvas.getSurface()) {
            return surface != null ? new EdgePixelGrid(m[0], m[4], m[2], m[5]) : null;
        }
    }
}
