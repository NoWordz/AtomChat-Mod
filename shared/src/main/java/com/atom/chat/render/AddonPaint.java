package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.AtomChatLog;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Picture;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.types.Rect;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Keeps an add-on's canvas and failed partial paint out of the host frame. */
final class AddonPaint {
    private record Warning(String part, Class<?> implementation) {}
    private static final Set<Warning> WARNED = new HashSet<>();

    private AddonPaint() {}

    static boolean draw(Canvas target, Rect bounds, Path clip, Consumer<Canvas> draw, Object callback, String part) {
        Picture recording;
        try (var recorder = new PictureRecorder()) {
            draw.accept(recorder.beginRecording(bounds));
            recording = recorder.finishRecordingAsPicture();
        } catch (RuntimeException failure) {
            report(part, callback, failure);
            return false;
        }
        try (recording) {
            target.save();
            try {
                if (clip == null) target.clipRect(bounds);
                else target.clipPath(clip, CornerGeometry.ANTIALIAS);
                target.drawPicture(recording);
            } finally {
                target.restore();
            }
        }
        return true;
    }

    static void report(String part, Object callback, RuntimeException failure) {
        if (WARNED.add(new Warning(part, callback.getClass()))) {
            AtomChat.LOGGER.warn(AtomChatLog.message("ui", "Could not draw appearance add-on {} ({})"),
                    part, callback.getClass().getName(), failure);
        }
    }
}
