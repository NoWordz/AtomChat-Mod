package com.atom.chat.page;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.AvatarRenderer;
import com.atom.chat.image.SkinResolver;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual page without a client window; no production test hooks. */
class ProfileAvatarInteractionTest {
    @TempDir Path directory;
    UiLayout layout = UiLayout.ofRoot(0, 0, 420, 1000);
    ProfilePage page;
    AvatarStore store;
    int picks, clears, bannerPicks, bannerClears;
    AtomChatConfig.AvatarStyle oldStyle;
    boolean oldMotion;
    Object unsafe;
    Class<?> unsafeType;
    Object singletonBase, oldClient;
    long singletonOffset;
    Object skin;
    Image image;
    final List<Map<Object, Image>> caches = new ArrayList<>();
    final List<Image> oldImages = new ArrayList<>();

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return directory.resolve("config"); }
            public Path gameDir() { return directory.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        oldStyle = AtomChatConfig.get().avatarStyle;
        oldMotion = AtomChatConfig.get().animationEnabled;
        AtomChatConfig.get().avatarStyle = AtomChatConfig.AvatarStyle.ATOMCHAT;
        AtomChatConfig.get().animationEnabled = true;
        unsafeType = Class.forName("sun.misc.Unsafe");
        Field u = unsafeType.getDeclaredField("theUnsafe"); u.setAccessible(true);
        unsafe = u.get(null);
        Class<?> client;
        try { client = Class.forName("net.minecraft.client.Minecraft"); }
        catch (ClassNotFoundException e) { client = Class.forName("net.minecraft.client.MinecraftClient"); }
        Class<?> type = client;
        Field singleton = Arrays.stream(client.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == type).findFirst().orElseThrow();
        singletonBase = unsafeType.getMethod("staticFieldBase",Field.class).invoke(unsafe,singleton);
        singletonOffset = (long) unsafeType.getMethod("staticFieldOffset",Field.class).invoke(unsafe,singleton);
        oldClient = unsafeType.getMethod("getObject",Object.class,long.class).invoke(unsafe,singletonBase,singletonOffset);
        replaceClient(unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,client));
        skin = SkinResolver.getSkin(null, "-");
        try (Surface s = Surface.makeRasterN32Premul(8, 8)) {
            s.getCanvas().clear(0xFFFF0000); image = s.makeImageSnapshot();
        }
        for (String name : List.of("FACE_CACHE", "SKIN_FACE_CACHE", "HAT_CACHE")) {
            Field f = AvatarRenderer.class.getDeclaredField(name); f.setAccessible(true);
            Map<Object, Image> cache = (Map<Object, Image>) f.get(null);
            caches.add(cache); oldImages.add(cache.put(skin, image));
        }
        store = new AvatarStore(directory.resolve("avatar"));
        page = new ProfilePage(new ProfilePage.Handler() {
            public void openAvatarPicker() { picks++; }
            public void clearAvatar() { clears++; }
            public void openBannerPicker() { bannerPicks++; }
            public void clearBanner() { bannerClears++; }
            public boolean copyText(String text) { return true; }
            public void beginSignatureEdit() {}
            public void endSignatureEdit(boolean commit) {}
        }, store);
    }

    void replaceClient(Object value) throws Exception {
        unsafeType.getMethod("putObject",Object.class,long.class,Object.class)
                .invoke(unsafe,singletonBase,singletonOffset,value);
    }
    @AfterEach void restore() throws Exception {
        for (int i = 0; i < caches.size(); i++) {
            if (oldImages.get(i) == null) caches.get(i).remove(skin);
            else caches.get(i).put(skin, oldImages.get(i));
        }
        if (image != null) image.close();
        if (unsafe != null && singletonBase != null) replaceClient(oldClient);
        AtomChatConfig.get().avatarStyle = oldStyle;
        AtomChatConfig.get().animationEnabled = oldMotion;
    }

    UiLayout.Rect rect(String name) throws Exception {
        Method m = ProfilePage.class.getDeclaredMethod(name, UiLayout.class, float.class);
        m.setAccessible(true); return (UiLayout.Rect) m.invoke(page, layout, 0F);
    }
    void avatar() throws Exception {
        var a = rect("avatarRect"); page.onClick(a.x()+a.w()/2, a.y()+a.h()/2, layout, 0);
    }
    void menu(int index) throws Exception {
        var a = rect("avatarRect");
        float h = Math.min(UiTokens.MENU_H, layout.list.h());
        float top = a.bottom()+UiTokens.s(6);
        if (top+h > layout.list.bottom()) top = a.y()-UiTokens.s(6)-h;
        top = Math.max(layout.list.y(), Math.min(top, layout.list.bottom()-h));
        page.onClick(a.x()+a.w()/2, top+(index+0.5F)*h/2, layout, 0);
    }
    void frame(Surface s, float x, float y) throws Exception {
        Field clock = ProfilePage.class.getDeclaredField("lastFrameMs"); clock.setAccessible(true);
        clock.setLong(page, System.currentTimeMillis()-16);
        s.getCanvas().clear(0);
        int depth = s.getCanvas().getSaveCount();
        page.render(s.getCanvas(), layout, x, y, 0);
        assertEquals(depth, s.getCanvas().getSaveCount(), "profile drawing leaked its canvas stack");
    }
    int pixel(Surface s, int x, int y) {
        try (Bitmap b = new Bitmap()) {
            b.allocN32Pixels(s.getWidth(), s.getHeight()); assertTrue(s.readPixels(b, 0, 0));
            byte[] p = b.readPixels(); int i = (y*s.getWidth()+x)*4;
            return (p[i+3]&255)<<24 | (p[i+2]&255)<<16 | (p[i+1]&255)<<8 | p[i]&255;
        }
    }
    int redWidth(Surface s, int y) {
        int first = s.getWidth(), last = -1;
        try (Bitmap b = new Bitmap()) {
            b.allocN32Pixels(s.getWidth(), s.getHeight()); assertTrue(s.readPixels(b,0,0));
            byte[] p = b.readPixels();
            for (int x=0;x<s.getWidth();x++) {
                int i=(y*s.getWidth()+x)*4;
                if ((p[i+2]&255)>220 && (p[i+1]&255)<100 && (p[i]&255)<100) {
                    first=Math.min(first,x); last=x;
                }
            }
        }
        return last<first ? 0 : last-first+1;
    }

    @Test void nativeAvatarOpensItsManagementMenuOnFirstEntry() throws Exception {
        avatar(); menu(0); assertEquals(1,picks,"native avatar menu must reach the picker");
        assertEquals(0,bannerPicks,"avatar click must not fall through to the banner");
    }
    @Test void nativeBadgeRemainsAShortcutToThePicker() throws Exception {
        var b=rect("badgeRect"); page.onClick(b.x()+b.w()/2,b.y()+b.h()/2,layout,0);
        assertEquals(1,picks);
    }
    @Test void nativeHoverBrightensAndBouncesTheActualAvatar() throws Exception {
        var a=rect("avatarRect"); int x=(int)(a.x()+a.w()/2), y=(int)(a.y()+a.h()/2);
        try (Surface s=Surface.makeRasterN32Premul(420,1000)) {
            frame(s,-1,-1); int before=pixel(s,x,y), width=redWidth(s,y);
            for(int i=0;i<16;i++) frame(s,x,y);
            assertNotEquals(before,pixel(s,x,y),"hover scrim must change the visible bitmap");
            assertTrue(redWidth(s,y)>width,"the avatar must visibly expand on hover");
        }
    }
    @Test void switchingBackRestoresNativeActionsAndDiscardsOldMenu() throws Exception {
        avatar();
        AtomChatConfig.get().avatarStyle=AtomChatConfig.AvatarStyle.E33CHAT;
        try(Surface s=Surface.makeRasterN32Premul(420,1000)){frame(s,-1,-1);}
        AtomChatConfig.get().avatarStyle=AtomChatConfig.AvatarStyle.ATOMCHAT;
        menu(0); assertEquals(0,picks,"a menu hidden by a style switch must not stay armed");
        avatar(); menu(0); assertEquals(1,picks,"native actions must return without recreating the page");
    }
    @Test void e33AndOtherPlayersCannotEditOrClickThroughAvatarIntoBanner() throws Exception {
        AtomChatConfig.get().avatarStyle=AtomChatConfig.AvatarStyle.E33CHAT;
        var a=rect("avatarRect");
        page.onClick(a.x()+a.w()/2,a.y()+2,layout,0);
        page.onRightClick(a.x()+a.w()/2,a.y()+2,layout,0);
        assertEquals(0,picks); assertEquals(0,bannerPicks); assertEquals(0,bannerClears);
        AtomChatConfig.get().avatarStyle=AtomChatConfig.AvatarStyle.ATOMCHAT;
        page.setSubject(PlayerRef.of(UUID.fromString("00000000-0000-0000-0000-000000000016"),"Other"));
        avatar(); menu(0); assertEquals(0,picks); assertEquals(0,clears);
        page.resetSubject(); avatar(); menu(0); assertEquals(1,picks);
    }
    @Test void clearRequiresAnAvatarAndTwoClicksAndRearmsAfterTimeout() throws Exception {
        avatar(); menu(1); menu(1); assertEquals(0,clears,"skin fallback cannot be cleared");
        page.onClick(layout.list.x(),layout.list.bottom()-1,layout,0);
        Files.createDirectories(directory.resolve("avatar"));
        Files.write(directory.resolve("avatar/avatar.png"),new byte[]{1}); store.refresh();
        avatar(); menu(1); assertEquals(0,clears); menu(1); assertEquals(1,clears);
        avatar(); menu(1);
        Field armed=ProfilePage.class.getDeclaredField("clearArmedAt"); armed.setAccessible(true);
        armed.setLong(page,System.currentTimeMillis()-3001);
        menu(1); assertEquals(1,clears,"expired confirmation must only rearm");
        menu(1); assertEquals(2,clears);
    }
    @Test void shortViewportMenuUsesItsVisiblePositionAndRejectsOffscreenActions() throws Exception {
        layout=UiLayout.ofRoot(0,0,420,500);
        avatar(); menu(0); assertEquals(1,picks,"clipped-below menu must move into the viewport");
        avatar(); page.onClick(210,layout.list.bottom()+1,layout,0);
        assertEquals(1,picks,"offscreen clicks may dismiss but must not execute menu rows");
    }
    @Test void openMenuDrawsWithoutLeakingWithMotionOnAndOff() throws Exception {
        var a=rect("avatarRect");
        int x=(int)(a.x()+a.w()/2-20), y=(int)(a.bottom()+UiTokens.s(6)+UiTokens.MENU_H/4);
        try(Surface s=Surface.makeRasterN32Premul(420,1000)) {
            for(boolean motion:new boolean[]{true,false}) {
                AtomChatConfig.get().animationEnabled=motion;
                page.resetSubject(); frame(s,-1,-1); int before=pixel(s,x,y);
                avatar();
                for(int i=0;i<16;i++) frame(s,-1,-1);
                assertNotEquals(before,pixel(s,x,y),"open menu must actually paint, with balanced layers");
                int oldPicks=picks; menu(0); assertEquals(oldPicks+1,picks);
            }
        }
    }
    @Test void changingSubjectsDiscardsAnAlreadyOpenNativeMenu() throws Exception {
        avatar();
        page.setSubject(PlayerRef.of(UUID.fromString("00000000-0000-0000-0000-000000000016"),"Other"));
        page.resetSubject(); menu(0);
        assertEquals(0,picks,"returning to self must not revive the previous menu");
        avatar(); menu(0); assertEquals(1,picks);
    }

    @Test void renderTheDefaultBannerDotSurfaces() throws Exception {
        var cfg=AtomChatConfig.get();
        var saved=com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean bannerEnabled=cfg.customBannerEnabled;
        try(var locale=com.atom.chat.render.PreviewLanguage.install("zh_cn");var surface=Surface.makeRasterN32Premul(900,320)) {
            cfg.customBannerEnabled=false;
            var c=surface.getCanvas();c.clear(0xFF30343A);
            var draw=ProfilePage.class.getDeclaredMethod("drawBanner",io.github.humbleui.skija.Canvas.class,UiLayout.class,float.class);
            draw.setAccessible(true);
            for(int i=0;i<2;i++) {
                com.atom.chat.theme.ThemeService.apply(cfg,i==0?"minimal":"raven");
                var banner=rect("bannerRect");
                com.atom.chat.render.SkiaFontRenderer.drawTextCentered(c,
                        com.atom.chat.font.FontManager.font(UiTokens.FONT_NAME),
                        i==0?"浅色 · 默认头图":"深色 · 默认头图",225+i*450,27,0xFFFFFFFF);
                c.save();
                try {
                    c.translate(i*450+20-banner.x(),60-banner.y());
                    draw.invoke(page,c,layout,0F);
                } finally{c.restore();}
            }
            var out=Path.of(System.getProperty("atomchat.projectDir",".")).resolve("build/preview/profile-banner-dots.png");
            Files.createDirectories(out.getParent());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(out,data.getBytes());}
        } finally{saved.applyTo(cfg);cfg.customBannerEnabled=bannerEnabled;}
    }
}
