package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.types.Rect;

import java.util.List;

/** A rounded compositing region in GUI coordinates. */
public record BlurMask(UiLayout.Rect rect, float radius, float alpha) {
    static UiLayout.Rect capture(List<BlurMask> masks, double guiScale, int framebufferWidth, int framebufferHeight) {
        int left = framebufferWidth;
        int top = framebufferHeight;
        int right = 0;
        int bottom = 0;
        for (BlurMask mask : masks) {
            var rect = mask.rect();
            left = Math.min(left, Math.max(0, (int) Math.floor(rect.x() * guiScale)));
            top = Math.min(top, Math.max(0, (int) Math.floor(rect.y() * guiScale)));
            right = Math.max(right, Math.min(framebufferWidth, (int) Math.ceil(rect.right() * guiScale)));
            bottom = Math.max(bottom, Math.min(framebufferHeight, (int) Math.ceil(rect.bottom() * guiScale)));
        }
        return new UiLayout.Rect((float) (left / guiScale), (float) (top / guiScale),
                (float) ((right - left) / guiScale), (float) ((bottom - top) / guiScale));
    }

    Rect textureCoords(double guiScale, int captureX, int captureBottom,
                       double textureWidthInDevicePixels, double textureHeightInDevicePixels) {
        // The rounded shader flips V. Keep samples pinned to the framebuffer while masks move.
        return Rect.makeLTRB((float) ((rect.x() * guiScale - captureX) / textureWidthInDevicePixels),
                (float) (1 - (captureBottom - rect.y() * guiScale) / textureHeightInDevicePixels),
                (float) ((rect.right() * guiScale - captureX) / textureWidthInDevicePixels),
                (float) (1 - (captureBottom - rect.bottom() * guiScale) / textureHeightInDevicePixels));
    }
}
