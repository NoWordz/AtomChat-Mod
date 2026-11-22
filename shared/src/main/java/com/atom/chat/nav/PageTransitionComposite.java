package com.atom.chat.nav;

import com.atom.chat.ui.PanelBackground;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.CardBackdrop;
import com.atom.chat.render.DotTexture;
import com.atom.chat.render.EdgePixelGrid;
import com.atom.chat.render.SettingsLabelShadow;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

/** Real page commands frozen on departure; only the target runs business draw code. */
public final class PageTransitionComposite implements AutoCloseable {
    public interface Host {
        NavPage topNav();
        /** A local tab changes the body even when the navigation destination is unchanged. */
        default int bodyVariant() { return 0; }
        default float bodyClipTop(UiLayout layout) { return layout.header.bottom(); }
        void drawArrivingBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY);
        void drawArrivingHeader(Canvas canvas, UiLayout layout, int mouseX, int mouseY, float vmx, float vmy);
        /** Reports item motion; the envelope still owns entry and exit for the entire body. */
        boolean arrivalStaggersSurfaces();
    }
    private static final int MAX_COMMANDS = 12_000;
    private final Host host;
    private final InterruptiblePageMotion motion = new InterruptiblePageMotion();
    private final UiStagger.Entrance entrance = new UiStagger.Entrance();
    private Paint alphaPaint = new Paint();
    private NavPage stamped;
    private int stampedBodyVariant;
    private Picture lastFrame, departing, lastMask, departingMask;
    private InterruptiblePageMotion.Frame lastMotion;
    private float lastClipTop;
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
        int variant = host.bodyVariant();
        if (stamped == null) {
            stamped = top; stampedBodyVariant = variant;
            com.atom.chat.diagnostics.FrameProfile.pageMotion(false);
            return;
        }
        if (!top.equals(stamped) || variant != stampedBodyVariant) {
            if (top.equals(stamped)) com.atom.chat.diagnostics.FrameSegments.navigation("chip",
                    top.page().name()+":"+top.param()+":"+variant);
            boolean interrupted = !motion.sample(nowMs).settled();
            arrivalFromPushedToRoot = !stamped.isRoot() && top.isRoot();
            if (AtomChatConfig.performanceModeActive()) clearPictures();
            else freezeLastFrame();
            navigationOverflow = interrupted && departing != null
                    && departing.getApproximateOpCount() > MAX_COMMANDS;
            motion.retarget(nowMs, backwards || arrivalFromPushedToRoot,
                    style() == AtomChatConfig.PageNavStyle.ZOOM, Animations.enabled());
            backwards = false;
            entrance.markEntered(nowMs);
            stamped = top;
            stampedBodyVariant = variant;
            arrivalEvent = true;
        }
        if (!Animations.enabled()) motion.finish();
        com.atom.chat.diagnostics.FrameProfile.pageMotion(!overlaySettled(nowMs));
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
    public UiLayout.Rect paintedBodyRect(UiLayout.Rect rect) {
        if(bounds==null)return rect;
        var frame=motion.sample(frameTime);
        return new UiLayout.Rect(frame.forwardX(rect.x(),bounds.x()+bounds.w()/2),frame.forwardY(rect.y(),bounds.y()+bounds.h()/2),rect.w()*frame.scale(),rect.h()*frame.scale());
    }
    public float paintedBodyScale() {return bounds==null?1:motion.sample(frameTime).scale();}
    public float bodyY(float y) {
        return bounds == null ? y : motion.sample(frameTime).inverseY(y, bounds.y() + bounds.h() / 2);
    }
    public void drawArrivingFrame(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  float vmx, float vmy, long nowMs, boolean settledPanel) {
        frameTime = nowMs;
        UiLayout.Rect panel = layout.rect();
        if (bounds != null && !bounds.equals(panel)) { clearPictures(); motion.finish(); }
        bounds = panel;
        // Without motion, an unsettled panel can paint on its current pixel grid directly.
        if ((AtomChatConfig.performanceModeActive() || (!settledPanel && !Animations.enabled())) && !CardBackdrop.active()) {
            clearPictures();
            motion.finish();
            UiStagger.atFrame(nowMs, () -> {
                host.drawArrivingBody(canvas, layout, mouseX, mouseY);
                host.drawArrivingHeader(canvas, layout, mouseX, mouseY, vmx, vmy);
            });
            return;
        }
        boolean settingsBody = stamped != null
                && (stamped.page() == AppPage.SETTINGS_SECTION || stamped.page() == AppPage.SERVER_CONFIG);
        boolean settledBody = overlaySettled(nowMs);
        Rect cull = Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h());
        Picture target;
        Picture mask = null;
        try (PictureRecorder recorder = new PictureRecorder()) {
            Canvas recording = recorder.beginRecording(cull);
            Runnable body = () -> UiStagger.atFrame(nowMs, () -> host.drawArrivingBody(recording, layout, mouseX, mouseY));
            Runnable draw = settledPanel && settledBody ? () -> EdgePixelGrid.record(recording, canvas, body) : body;
            boolean snapTitles = settledPanel && settledBody && settingsBody && UiStagger.itemsSettled(entrance, nowMs);
            mask = CardBackdrop.recordMasks(cull, snapTitles
                    ? () -> DotTexture.recordTitles(recording, canvas,
                            () -> SettingsLabelShadow.onDeviceGrid(recording, canvas.getLocalToDeviceAsMatrix33(), draw))
                    : draw);
            target = recorder.finishRecordingAsPicture();
        } catch (RuntimeException | Error failure) {
            close(mask);
            throw failure;
        }

        close(lastFrame);
        close(lastMask);
        lastFrame = target;
        lastMask = mask;
        lastClipTop = host.bodyClipTop(layout);
        if (settledBody) {
            lastMotion = null;
            close(departing);
            close(departingMask);
            departing = null;
            departingMask = null;
            if (lastMask != null) CardBackdrop.applyMasks(canvas, cull, lastMask::playback);
            lastFrame.playback(canvas);
        } else {
            lastMotion = motion.sample(nowMs);
            // The backdrop sits below all page content. Keep the same composition when freezing an interruption.
            if (lastMask != null || departingMask != null) {
                CardBackdrop.applyMasks(canvas, cull,
                        layer -> drawTransition(layer, lastMask, departingMask, panel, lastClipTop, lastMotion));
            }
            drawTransition(canvas, lastFrame, departing, panel, lastClipTop, lastMotion);
        }
        Runnable header = () -> UiStagger.atFrame(nowMs,
                () -> host.drawArrivingHeader(canvas, layout, mouseX, mouseY, vmx, vmy));
        if (settledPanel && settingsBody) SettingsLabelShadow.onDeviceGrid(canvas, null, header);
        else header.run();
    }

    public void drawArrivingFrame(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  float vmx, float vmy, long nowMs) {
        drawArrivingFrame(canvas, layout, mouseX, mouseY, vmx, vmy, nowMs, false);
    }

    /** Only an interruption needs to flatten the previously displayed pair of bodies. */
    private void freezeLastFrame() {
        if (lastMotion == null || bounds == null) {
            close(departing);
            close(departingMask);
            departing = lastFrame;
            departingMask = lastMask;
        } else {
            Picture frozen = recordTransition(lastFrame, departing);
            Picture frozenMask;
            try {
                frozenMask = lastMask == null && departingMask == null ? null : recordTransition(lastMask, departingMask);
            } catch (RuntimeException | Error failure) {
                frozen.close();
                throw failure;
            }
            close(lastFrame);
            close(lastMask);
            close(departing);
            close(departingMask);
            departing = frozen;
            departingMask = frozenMask;
        }
        lastFrame = null;
        lastMask = null;
        lastMotion = null;
    }

    private Picture recordTransition(Picture target, Picture previous) {
        try (PictureRecorder recorder = new PictureRecorder()) {
            Canvas canvas = recorder.beginRecording(Rect.makeXYWH(bounds.x(), bounds.y(), bounds.w(), bounds.h()));
            drawTransition(canvas, target, previous, bounds, lastClipTop, lastMotion);
            return recorder.finishRecordingAsPicture();
        }
    }

    private void drawTransition(Canvas canvas, Picture target, Picture previous, UiLayout.Rect panel,
                                float clipTop, InterruptiblePageMotion.Frame frame) {
        int save = canvas.save();
        try {
            canvas.clipRect(Rect.makeLTRB(panel.x(), clipTop, panel.right(), panel.bottom()));
            if (frame.alpha() == 0 && previous != null) {
                previous.playback(canvas);
            } else {
                PanelBackground.clipExterior(canvas, panel);
                drawLayer(canvas, previous, panel, 1 - frame.alpha(), frame.exitDx(), frame.exitScale());
                drawLayer(canvas, target, panel, frame.alpha(), frame.dx(), frame.scale());
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawLayer(Canvas canvas, Picture picture, UiLayout.Rect panel,
                           float alpha, float dx, float scale) {
        if (picture == null || alpha <= 0) return;
        int save = canvas.save();
        try {
            alphaPaint.setAlphaf(alpha);
            Rect layerBounds = Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h());
            canvas.saveLayer(layerBounds, alphaPaint);
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
        Picture body = compactPicture(context, info, departing);
        Picture mask = null;
        try {
            if (departingMask != null) mask = compactPicture(context, info, departingMask);
        } catch (RuntimeException | Error failure) {
            body.close();
            throw failure;
        }
        departing.close();
        close(departingMask);
        departing = body;
        departingMask = mask;
        navigationOverflow = false;
    }

    private Picture compactPicture(DirectContext context, ImageInfo info, Picture picture) {
        try (Surface surface = Surface.makeRenderTarget(context, false, info, 0, SurfaceOrigin.BOTTOM_LEFT, null)) {
            if (surface == null) throw new IllegalStateException("Navigation snapshot surface unavailable");
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(deviceDensity, deviceDensity);
            canvas.translate(-bounds.x(), -bounds.y());
            picture.playback(canvas);
            // No CPU readback or synchronous GPU wait. Immutable owned image retains its target.
            context.flushAndSubmit(surface, false);
            try (Image image = surface.makeImageSnapshot(); PictureRecorder recorder = new PictureRecorder()) {
                Rect cull = Rect.makeXYWH(bounds.x(), bounds.y(), bounds.w(), bounds.h());
                Canvas recording = recorder.beginRecording(cull);
                recording.drawImageRect(image, cull);
                return recorder.finishRecordingAsPicture();
            }
        }
    }

    private static void close(Picture picture) {
        if (picture != null) picture.close();
    }

    private void clearPictures() {
        navigationOverflow = false;
        close(lastFrame);
        close(departing);
        close(lastMask);
        close(departingMask);
        lastFrame = departing = lastMask = departingMask = null;
        lastMotion = null;
    }
    public void reset() {
        if (alphaPaint.isClosed()) alphaPaint = new Paint();
        clearPictures(); motion.finish(); stamped = null; bounds = null; arrivalEvent = false;
    }
    @Override public void close() { clearPictures(); if (!alphaPaint.isClosed()) alphaPaint.close(); }
}
