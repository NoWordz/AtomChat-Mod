package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

/** Real page commands frozen on departure; only the target runs business draw code. */
public final class PageTransitionComposite implements AutoCloseable {
    public interface Host {
        NavPage topNav();
        void drawArrivingBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY);
        void drawArrivingHeader(Canvas canvas, UiLayout layout, int mouseX, int mouseY, float vmx, float vmy);
        /** Reports item motion; the envelope still owns entry and exit for the entire body. */
        boolean arrivalStaggersSurfaces();
    }
    private static final int MAX_COMMANDS = 12_000;
    private final Host host;
    private final InterruptiblePageMotion motion = new InterruptiblePageMotion();
    private final UiStagger.Entrance entrance = new UiStagger.Entrance();
    private final Paint alphaPaint = new Paint();
    private NavPage stamped;
    private Picture lastFrame, departing;
    private UiLayout.Rect bounds;
    private boolean arrivalEvent, arrivalFromPushedToRoot, backwards;
    private long frameTime;
    private float deviceDensity = 1;
    private boolean snapshotBudgetExceeded;
    private boolean navigationOverflow;

    public PageTransitionComposite(Host host) { this.host = host; }
    public void navigationDirection(boolean backwards) { this.backwards = backwards; }
    public void deviceDensity(float density) { deviceDensity = Math.max(1, density); }
    public boolean snapshotBudgetExceeded() { return snapshotBudgetExceeded; }

    public void beginFrame(long nowMs) {
        frameTime = nowMs;
        NavPage top = host.topNav();
        if (top == null) return;
        if (stamped == null) { stamped = top; return; }
        if (!top.equals(stamped)) {
            boolean interrupted = !motion.sample(nowMs).settled();
            arrivalFromPushedToRoot = !stamped.isRoot() && top.isRoot();
            if (departing != null) departing.close();
            departing = lastFrame;
            lastFrame = null;
            navigationOverflow = interrupted && departing != null
                    && departing.getApproximateOpCount() > MAX_COMMANDS;
            motion.retarget(nowMs, backwards || arrivalFromPushedToRoot,
                    style() == AtomChatConfig.PageNavStyle.ZOOM, Animations.enabled());
            backwards = false;
            entrance.markEntered(nowMs);
            stamped = top;
            arrivalEvent = true;
        }
        if (!Animations.enabled()) motion.finish();
    }
    public boolean arrivalFromPushedToRoot() { return arrivalFromPushedToRoot; }
    public boolean consumeArrivalEvent() { boolean e = arrivalEvent; arrivalEvent = false; return e; }
    public UiStagger.Entrance entrance() { return entrance; }
    public float overlayAlpha(long nowMs) { return motion.sample(nowMs).alpha(); }
    public float overlaySlideDx(long nowMs) { return motion.sample(nowMs).dx(); }
    public float overlayZoomScale(long nowMs) { return motion.sample(nowMs).scale(); }
    public boolean overlaySettled(long nowMs) { return !Animations.enabled() || motion.sample(nowMs).settled(); }
    public AtomChatConfig.PageNavStyle style() {
        return AtomChatConfig.get().pageNavStyle;
    }

    /** Last painted geometry, not a later wall clock estimate, owns pointer hits. */
    public float bodyX(float x) {
        return bounds == null ? x : motion.sample(frameTime).inverseX(x, bounds.x() + bounds.w() / 2);
    }
    public float bodyY(float y) {
        return bounds == null ? y : motion.sample(frameTime).inverseY(y, bounds.y() + bounds.h() / 2);
    }
    public void drawArrivingFrame(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  float vmx, float vmy, long nowMs) {
        frameTime = nowMs;
        UiLayout.Rect panel = layout.rect();
        if (bounds != null && !bounds.equals(panel)) { clearPictures(); motion.finish(); }
        bounds = panel;
        Rect cull = Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h());
        Picture target;
        try (PictureRecorder recorder = new PictureRecorder()) {
            Canvas recording = recorder.beginRecording(cull);
            UiStagger.atFrame(nowMs, () -> host.drawArrivingBody(recording, layout, mouseX, mouseY));
            target = recorder.finishRecordingAsPicture();
        }
        try {
            Picture composed;
            InterruptiblePageMotion.Frame frame = motion.sample(nowMs);
            if (overlaySettled(nowMs)) {
                composed = target;
                target = null;
                if (departing != null) { departing.close(); departing = null; }
            } else {
                try (PictureRecorder recorder = new PictureRecorder()) {
                    Canvas recording = recorder.beginRecording(cull);
                    if (frame.alpha() == 0 && departing != null) {
                        // Replaying the exact command stream avoids an extra AA clip/layer
                        // rounding the same interrupted pixels a second time.
                        departing.playback(recording);
                    } else {
                    SkiaDraw.clip(recording, panel.x(), panel.y(), panel.w(), panel.h(), UiTokens.panelRadius());
                    recording.clipRect(Rect.makeLTRB(panel.x(), layout.header.bottom(), panel.right(), panel.bottom()));
                    if (departing != null) drawLayer(recording, departing, panel,
                            1 - frame.alpha(), frame.exitDx(), frame.exitScale());
                    drawLayer(recording, target, panel, frame.alpha(), frame.dx(), frame.scale());
                    }
                    composed = recorder.finishRecordingAsPicture();
                }
            }
            if (lastFrame != null) lastFrame.close();
            lastFrame = composed;
            // Expand commands instead of retaining a nested transition Picture chain.
            lastFrame.playback(canvas);
        } finally { if (target != null) target.close(); }
        host.drawArrivingHeader(canvas, layout, mouseX, mouseY, vmx, vmy);
    }
    private void drawLayer(Canvas canvas, Picture picture, UiLayout.Rect panel,
                           float alpha, float dx, float scale) {
        if (alpha <= 0) return;
        int save = canvas.save();
        try {
            alphaPaint.setAlphaf(alpha);
            canvas.saveLayer(Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h()), alphaPaint);
            canvas.translate(panel.x() + panel.w() / 2 + dx, panel.y() + panel.h() / 2);
            canvas.scale(scale, scale);
            canvas.translate(-panel.x() - panel.w() / 2, -panel.y() - panel.h() / 2);
            picture.playback(canvas);
        } finally { canvas.restoreToCount(save); }
    }
    public boolean needsCompaction() {
        return navigationOverflow && departing != null;
    }
    /** Rare overflow only, outside the bridge pass. The borrowed context is never stored. */
    public void compact(DirectContext context) {
        if (!needsCompaction() || bounds == null) return;
        int width = Math.max(1, (int) Math.ceil(bounds.w() * deviceDensity));
        int height = Math.max(1, (int) Math.ceil(bounds.h() * deviceDensity));
        // Refuse an oversized target rather than secretly reducing snapshot resolution.
        if (width > 8192 || height > 8192 || (long) width * height > 16_777_216L) {
            snapshotBudgetExceeded = true;
            reset();
            return;
        }
        ImageInfo info = ImageInfo.makeN32Premul(width, height);
        try (Surface surface = Surface.makeRenderTarget(context, false, info, 0, SurfaceOrigin.BOTTOM_LEFT, null)) {
            if (surface == null) throw new IllegalStateException("Navigation snapshot surface unavailable");
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(deviceDensity, deviceDensity);
            canvas.translate(-bounds.x(), -bounds.y());
            departing.playback(canvas);
            // No CPU readback or synchronous GPU wait. Immutable owned image retains its target.
            context.flushAndSubmit(surface, false);
            try (Image image = surface.makeImageSnapshot(); PictureRecorder recorder = new PictureRecorder()) {
                Canvas recording = recorder.beginRecording(Rect.makeXYWH(bounds.x(), bounds.y(), bounds.w(), bounds.h()));
                recording.drawImageRect(image, Rect.makeXYWH(bounds.x(), bounds.y(), bounds.w(), bounds.h()));
                Picture compacted = recorder.finishRecordingAsPicture();
                departing.close();
                departing = compacted;
                navigationOverflow = false;
            }
        }
    }
    private void clearPictures() {
        navigationOverflow = false;
        if (lastFrame != null) { lastFrame.close(); lastFrame = null; }
        if (departing != null) { departing.close(); departing = null; }
    }
    public void reset() { clearPictures(); motion.finish(); stamped = null; bounds = null; }
    @Override public void close() { clearPictures(); alphaPaint.close(); }
}
