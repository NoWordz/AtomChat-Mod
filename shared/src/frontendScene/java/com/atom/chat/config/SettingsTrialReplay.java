package com.atom.chat.config;

import com.atom.chat.ui.UiStagger;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.settings.SettingsDetailPage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.render.PreviewLanguage;
import com.atom.chat.theme.ThemeService;
import io.github.humbleui.skija.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Current or independently frozen production fixture; caller chooses an explicit classpath. No game window. */
public final class SettingsTrialReplay {
    // Keep the reviewed 0.9.8 light scene inputs stable across player-facing preset edits.
    private static final ThemeService.Preset LIGHT_REFERENCE = new ThemeService.Preset("minimal",
            0xFF2563D8,
            0xFF2563D8, 0xFFFFFFFF,
            0xFFB9CDE6, 0xFF1A1D21,
            0xFFF7F8FA, 0xFF151A22, 0xFF596273,
            0xFFFFFFFF, 0xFF4A6285,
            0xFFECEEF2, 0xFF626976,
            null, null, "small");

    public static void main(String[] args)throws Exception {
        run(Path.of(args[0]),args[1],args[2],Integer.parseInt(args[3]),args[4].equals("light"),
                args.length == 6 && args[5].equals("interaction"), Boolean.getBoolean("fcl.strictBody"), null);
    }

    static void run(Path out,String page,String locale,int width,boolean light,boolean interactions,boolean strict,
                    SettingsContractScene capture)throws Exception {
        Files.createDirectories(out);
        boolean hosting=page.equals("hosting");
        if (capture != null && Boolean.getBoolean("atomchat.fcl.forge")) {
            SharedSettingsFrontendTest.bootstrapMinecraft();
        }
        var test=Class.forName("com.atom.chat.config.SharedSettingsFrontendTest");var ctor=test.getDeclaredConstructor();ctor.setAccessible(true);Object owner=ctor.newInstance();
        var temp=test.getDeclaredField("temp");temp.setAccessible(true);temp.set(owner,out.resolve("isolated-config"));invoke(test,owner,"setup");
        try(var language=PreviewLanguage.install(locale)){
            var config = AtomChatConfig.get();
            if (light) LIGHT_REFERENCE.applyTo(config);
            config.panelOpacity = 0.6990596F;
            config.cardTint = 0.59866506F;
            config.animationEnabled = true;
            config.blurEnabled = false;
            for(String resource:List.of("/com/atom/lib/fonts/bundled-default.otf","/com/atom/lib/fonts/bundled-default-bold.otf"))if(SettingsTrialReplay.class.getResource(resource)==null)throw new AssertionError("MISSING_FONT "+resource);
            if (page.equals("about")) {
                AboutSettingsReplay.run(out, width, locale, light, capture);
                return;
            }
            var type=Class.forName("com.atom.chat.config.SharedSettingsFrontendTest$Pane");var create=type.getDeclaredConstructor(boolean.class,int.class,String.class);create.setAccessible(true);Object pane=create.newInstance(hosting,width,locale);
            var frame=type.getDeclaredMethod("frame",Surface.class,long.class);frame.setAccessible(true);var chip=type.getDeclaredMethod("chip",int.class);chip.setAccessible(true);var close=type.getDeclaredMethod("close");close.setAccessible(true);
            var timeline = interactions ? new Interactions(type, pane, hosting) : null;
            var samples = interactions ? Set.of(4, 32, 35, 39, 41, 48, 55, 58, 62, 65, 69, 73, 82, 89)
                    : Set.of(0,4,12,24,34,35,39,47,59,65,66,70,78,89);
            int mountedFrames = 0;
            Object route = strict ? field(type, pane, hosting ? "server" : "settings") : null;
            Method mounted = route == null ? null
                    : route.getClass().getMethod(hosting ? "hostingBodyMounted" : "historyBodyMounted");
            if (capture != null) capture.setup(type, pane, strict);
            List<String> frames=new ArrayList<>();
            try(var surface=Surface.makeRasterN32Premul(width+40,900)){
                int framesToPaint = capture != null && interactions ? 131 : 90;
                for(int i=0;i<framesToPaint;i++){
                    if (capture == null && !interactions) { if(i==35)chip.invoke(pane,1);if(i==66)chip.invoke(pane,0); }
                    final int tick=i;
                    System.setProperty("fcl.historyCaretMillis", Long.toString(1000L + tick * 16));
                    UiStagger.atFrame(1000+i*16,()->{
                        try {
                            if (capture != null) capture.beforeInput(tick);
                            if (capture != null && !interactions) { if(tick==35)chip.invoke(pane,1);if(tick==66)chip.invoke(pane,0); }
                            if (timeline != null) timeline.input(tick);
                            if (capture != null) capture.afterInput(tick);
                            frame.invoke(pane,surface,1000L+tick*16);
                            if (timeline != null) timeline.paintModal(surface.getCanvas());
                            if (capture != null && (samples.contains(tick) || capture.extraSample(tick))) capture.record(tick, surface);
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    if (strict) {
                        boolean expected = hosting ? interactions || i < 35 || i >= 66
                                : interactions ? i >= 1 : i >= 35 && i < 66;
                        if ((boolean) mounted.invoke(route) != expected) {
                            throw new AssertionError("FCL_ROUTE_SELECTION " + page + " frame=" + i);
                        }
                        if (expected) mountedFrames++;
                    }
                    if(capture == null && samples.contains(i))try(var image=surface.makeImageSnapshot();var png=image.encodeToData(EncodedImageFormat.PNG)){Files.write(out.resolve("frame-"+i+".png"),png.getBytes());frames.add(Integer.toString(i));}
                }
            }finally{close.invoke(pane);}
            if (strict) System.out.println("FCL_DEFAULT_STRICT_BODY frames=" + mountedFrames);
            if(capture == null) {
                Files.write(out.resolve("frames.txt"),frames);Files.writeString(out.resolve("conditions.txt"),"PRODUCTION_COMPONENT_SOFTWARE_ONLY\npage="+page+"\nscenario="+(interactions?"interaction":"navigation")+"\nlocale="+locale+"\nwidth="+width+"\nlight="+light+"\nanimation=true\nblur=false\nfont=packaged regular+bold\n");System.out.println("PRODUCTION_TRIAL_CAPTURE frames="+frames.size()+" page="+page);
            }
        }finally{invoke(test,owner,"restore");}
    }
    private static final class Interactions {
        private final Object pane;
        private final Class<?> type;
        private final boolean hosting;
        private final UiLayout layout;
        private final SettingsSectionPage history;
        private final SettingsDetailPage detail;
        private final ServerConfigPage server;
        private int cleared;

        Interactions(Class<?> type, Object pane, boolean hosting) throws Exception {
            this.pane = pane;
            this.type = type;
            this.hosting = hosting;
            layout = (UiLayout) field("layout");
            history = (SettingsSectionPage) field("settings");
            detail = (SettingsDetailPage) field("detail");
            server = (ServerConfigPage) field("server");
            history.setActionHandler(action -> { if (action.equals("history_clear")) cleared++; });
        }

        void input(int tick) throws Exception {
            if (tick == 1 && !hosting) {
                var chip = type.getDeclaredMethod("chip", int.class);
                chip.setAccessible(true);
                chip.invoke(pane, 1);
            }
            if (tick == 34 || tick == 41) invoke(type, pane, "fold");
            if (hosting) hostingInput(tick); else historyInput(tick);
        }

        private void historyInput(int tick) throws Exception {
            var keys = detail.numberEditInput();
            switch (tick) {
                case 52, 60 -> {
                    var days = history.rows(SettingsSection.CHAT).stream()
                            .filter(row -> row.slider() != null && row.slider().id().equals("history_retention"))
                            .findFirst().orElseThrow();
                    history.beginNumberEdit(days.slider());
                }
                case 54 -> {
                    require(keys.onKey('9', 0, 0) && keys.onChar('9', 0), "history numeric input");
                    require(history.editingNumberText().equals("79"), "single digit for key and char");
                }
                case 58 -> {
                    keys.onKey(256, 0, 0);
                    require(AtomChatConfig.get().historyRetentionDays == 7, "cancelled history entry");
                }
                case 62 -> {
                    keys.onKey(259, 0, 0);
                    keys.onKey('3', 0, 0);
                }
                case 65 -> {
                    keys.onKey(257, 0, 0);
                    require(AtomChatConfig.get().historyRetentionDays == 3, "committed history entry");
                }
                case 69 -> {
                    var anchor = history.actionAnchor(SettingsSection.CHAT, "history_clear", layout, 0);
                    var gate = new com.atom.chat.ui.input.InputGate(() -> false)
                            .add(new com.atom.chat.ui.ConfirmationInput(history::confirmation, x -> (float) x, y -> (float) y))
                            .add(new com.atom.chat.ui.input.InputHandler() {
                                @Override public com.atom.chat.ui.input.ClickTarget clickTarget(double x, double y, int button) {
                                    return button == 0 ? com.atom.chat.settings.SettingsDetailPage.rowClickTarget(
                                            history, SettingsSection.CHAT, layout, 0, (float) x, (float) y, () -> {}) : null;
                                }
                            });
                    double x = anchor.x() + 4, y = anchor.y() + anchor.h() / 2;
                    require(Boolean.TRUE.equals(gate.click(x, y, 0)), "history cleanup owns the press");
                    require(history.confirmation().hasPendingAction(), "press opens cleanup confirmation");
                    require(Boolean.TRUE.equals(gate.release(x, y, 0)) && history.confirmation().hasPendingAction(),
                            "release of the cleanup row stays consumed by its press");
                    require(cleared == 0, "history kept until confirmation");
                }
                case 83 -> {
                    history.confirmation().key(256);
                    require(!history.confirmation().hasPendingAction() && cleared == 0, "cleanup cancellation");
                }
                default -> { }
            }
        }

        private void hostingInput(int tick) throws Exception {
            if (tick == 52) {
                // Click the native displayed field, including narrow-layout stacking and partial folds.
                var bounds = hostingFieldHit("maxFileKb");
                server.click(bounds.x() + bounds.w() / 2, bounds.y() + bounds.h() / 2, 0);
                server.release();
            }
            if (tick == 54) {
                server.key('A', 2);
                server.key(259, 0);
                require(server.draft().text("maxFileKb").isEmpty(), "empty hosting draft remains observable");
            }
            if (tick == 61) {
                for (char digit : "512".toCharArray()) server.character(digit);
                require(server.draft().text("maxFileKb").equals("512"), "hosting draft edit");
            }
            if (tick == 66) server.key(257, 0);
            if (tick == 77) {
                var viewport = ServerConfigLayout.of(layout).scrollViewport();
                server.scroll(viewport.x() + viewport.w() / 2, viewport.y() + viewport.h() / 2, -4);
            }
        }

        private UiLayout.Rect hostingFieldHit(String id) throws Exception {
            var hits = ServerConfigPage.class.getDeclaredField("hits");
            hits.setAccessible(true);
            for (Object hit : (List<?>) hits.get(server)) {
                var field = hit.getClass().getDeclaredMethod("field");
                field.setAccessible(true);
                if (!id.equals(field.invoke(hit))) continue;
                var box = hit.getClass().getDeclaredMethod("box");
                box.setAccessible(true);
                return (UiLayout.Rect) box.invoke(hit);
            }
            throw new AssertionError("MISSING_INTERACTION displayed hosting field " + id);
        }

        void paintModal(Canvas canvas) {
            if (!hosting && history.confirmation().isActive()) {
                history.confirmation().render(canvas, layout.rect(), -1, -1, 16);
            }
        }

        private Object field(String name) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(pane);
        }

        private static void require(boolean condition, String description) {
            if (!condition) throw new AssertionError("MISSING_INTERACTION " + description);
        }
    }

    static Object field(Class<?> type, Object owner, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void invoke(Class<?> type,Object owner,String name)throws Exception{var method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner);}
}
