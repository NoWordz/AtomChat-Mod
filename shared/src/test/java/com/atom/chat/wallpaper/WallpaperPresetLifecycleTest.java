package com.atom.chat.wallpaper;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WallpaperPresetLifecycleTest {
    @TempDir Path tmp;
    private Object previousProvider,previousConfig;
    private Field provider,instance;
    private ExecutorService worker;
    @BeforeEach void setup()throws Exception {
        provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);previousProvider=provider.get(null);
        instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);previousConfig=instance.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
        instance.set(null,new AtomChatConfig());WallpaperStore.init(tmp.resolve("wallpaper"));
        Field f=WallpaperPresets.class.getDeclaredField("WORKER");f.setAccessible(true);worker=(ExecutorService)f.get(null);
    }
    @AfterEach void restore()throws Exception{WallpaperPresets.cancelPending();worker.submit(()->{}).get(10,TimeUnit.SECONDS);WallpaperImage.release();provider.set(null,previousProvider);instance.set(null,previousConfig);}
    @Test void latestSelectionWinsAndClearCannotResurrectAQueuedWallpaper()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        try {for(int i=0;i<100;i++)WallpaperPresets.apply(i%2==0?"fluid":"silk");WallpaperStore.clear();}
        finally{release.countDown();}
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(WallpaperStore.isSet());assertFalse(WallpaperPresets.loading());
        WallpaperPresets.apply("planes");WallpaperPresets.apply("silk");worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertEquals("silk",WallpaperStore.presetId());
        byte[] actual=Files.readAllBytes(WallpaperStore.current());
        try(var image=io.github.humbleui.skija.Image.makeFromEncoded(actual)) {
            assertArrayEquals(WallpaperPresets.renderPng("silk",image.getWidth(),image.getHeight()),actual);
        }
    }
    @Test void cancellationAndFinalPublicationUseTheSameMonitor()throws Exception {
        Thread cancel=new Thread(WallpaperPresets::cancelPending);
        synchronized(WallpaperStore.class){
            cancel.start();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(cancel.getState()!=Thread.State.BLOCKED&&cancel.isAlive()&&System.nanoTime()<until)Thread.yield();
            assertEquals(Thread.State.BLOCKED,cancel.getState(),"cancel cannot cross the store's publication critical section");
        }
        cancel.join(2000);assertFalse(cancel.isAlive());
    }
    @Test void viewportNeverWaitsOnStoreIoAndHighDensityPresetsKeepTheirResolution()throws Exception {
        synchronized(WallpaperStore.class) {
            Future<?> frame=java.util.concurrent.CompletableFuture.runAsync(()->{
                WallpaperPresets.viewport(1600,2400,10000);
                WallpaperPresets.viewport(1600,2400,10300);
                for(int i=0;i<10000;i++)WallpaperPresets.viewport(1600,2400,10400+i);
            });
            frame.get(2,TimeUnit.SECONDS);
        }
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        WallpaperPresets.apply("fluid");worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        try(var image=io.github.humbleui.skija.Image.makeFromEncoded(Files.readAllBytes(WallpaperStore.current()))) {
            assertTrue(image.getWidth()>=1600);assertTrue(image.getHeight()>=2400);
        }
        WallpaperImage.release();
        io.github.humbleui.skija.Image decoded=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(decoded==null&&System.nanoTime()<deadline){decoded=WallpaperImage.current(WallpaperStore.current());if(decoded==null)Thread.sleep(5);}
        assertNotNull(decoded);assertTrue(decoded.getHeight()>=2400,"preset decode cannot silently return to the 1024 cap");
        WallpaperImage.release();WallpaperStore.clear();
        WallpaperPresets.viewport(1800,2600,30000);WallpaperPresets.viewport(1800,2600,30300);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(WallpaperStore.isSet(),"resize cannot resurrect a cleared preset");
    }
    @Test void firstUseNeverOverridesExistingWallpaperAndClearStaysCleared()throws Exception {
        AtomChatConfig cfg=AtomChatConfig.get();cfg.wallpaperFirstUsePending=true;
        assertTrue(WallpaperStore.setPng(WallpaperPresets.renderPng("chalk",32,64)));
        byte[] before=Files.readAllBytes(WallpaperStore.current());WallpaperPresets.ensureFirstUse(cfg);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(cfg.wallpaperFirstUsePending);assertArrayEquals(before,Files.readAllBytes(WallpaperStore.current()));
        WallpaperStore.clear();WallpaperPresets.ensureFirstUse(cfg);worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(WallpaperStore.isSet());
    }
    @Test void newUsersStartWithoutWallpaperEvenWithTheOldFirstUseFlag()throws Exception {
        AtomChatConfig cfg=AtomChatConfig.get(); cfg.wallpaperFirstUsePending=true;
        WallpaperPresets.ensureFirstUse(cfg);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(WallpaperStore.isSet(),"first use must not silently select a wallpaper");
    }
    @Test void retiredGraphiteKeepsItsImageWithoutResizeRegeneration()throws Exception {
        byte[] original=WallpaperPresets.renderPng("planes",32,64);
        assertTrue(WallpaperStore.setPng(original));
        Path dir=WallpaperStore.current().getParent();
        Files.writeString(dir.resolve("selected-preset.txt"),"graphite");
        WallpaperStore.init(dir);
        assertEquals("",WallpaperStore.presetId(),"retired preset becomes an independent custom image");
        WallpaperPresets.viewport(800,1400,50000); WallpaperPresets.viewport(800,1400,50300);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertArrayEquals(original,Files.readAllBytes(WallpaperStore.current()));
    }
    @Test void retiredGraphiteKeepsItsHighResolutionAfterReloadAndResize()throws Exception {
        byte[] original=WallpaperPresets.renderPng("planes",800,1600);
        Path dir=installRetired(original);
        assertEquals("",WallpaperStore.presetId(),"retired source cannot be an active generation request");
        assertArrayEquals(original,Files.readAllBytes(WallpaperStore.current()));
        assertEquals(1600,loadCurrent().getHeight(),"retirement must retain the preset decode policy");
        WallpaperStore.init(dir);
        assertEquals(1600,loadCurrent().getHeight(),"source provenance must survive restart");
        WallpaperPresets.viewport(1200,2000,70000);WallpaperPresets.viewport(1200,2000,70300);
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(WallpaperPresets.loading());assertEquals("",WallpaperPresets.error());
        assertArrayEquals(original,Files.readAllBytes(WallpaperStore.current()),"resize must not redraw a retired source");
        assertEquals(1600,loadCurrent().getHeight());
    }
    @Test void everyCustomSetterClearsRetiredProvenanceAndKeepsThe1024Policy()throws Exception {
        byte[] original=WallpaperPresets.renderPng("planes",800,1600);
        Path external=tmp.resolve("picked.png");Files.write(external,original);
        for(int operation=0;operation<3;operation++) {
            Path dir=installRetired(original);
            assertEquals(1600,loadCurrent().getHeight());
            boolean set=switch(operation) {
                case 0 -> WallpaperStore.setPng(original);
                case 1 -> WallpaperStore.set(external);
                default -> WallpaperStore.set(WallpaperStore.current());
            };
            assertTrue(set);assertFalse(Files.exists(dir.resolve("selected-preset.txt")),"custom setter must remove the source marker: "+operation);
            assertEquals(1024,loadCurrent().getHeight(),"custom image policy cannot inherit retired preset metadata: "+operation);
            WallpaperStore.init(dir);
            assertEquals(1024,loadCurrent().getHeight(),"custom policy must persist after reload");
        }
    }
    @Test void clearAndFailedSelectionHaveTheCorrectRetiredMetadataLifetime()throws Exception {
        byte[] original=WallpaperPresets.renderPng("planes",800,1600);
        Path dir=installRetired(original);
        assertFalse(WallpaperStore.set(tmp.resolve("missing.png")));
        assertFalse(WallpaperStore.setPng(new byte[0]));
        assertEquals(1600,loadCurrent().getHeight(),"failed replacements must preserve the old source");
        assertTrue(WallpaperStore.clear());
        assertNull(WallpaperStore.current());assertFalse(Files.exists(dir.resolve("selected-preset.txt")));
        assertTrue(WallpaperStore.setPng(original));
        assertEquals(1024,loadCurrent().getHeight(),"cleared provenance cannot leak into a later custom wallpaper");
    }
    private Path installRetired(byte[] original)throws Exception {
        assertTrue(WallpaperStore.setPng(original));
        Path dir=WallpaperStore.current().getParent();
        Files.writeString(dir.resolve("selected-preset.txt"),"graphite");
        WallpaperStore.init(dir);
        return dir;
    }
    private io.github.humbleui.skija.Image loadCurrent()throws Exception {
        // Exercise revision-driven production invalidation; do not manually
        // release the old high-resolution image when testing custom setters.
        io.github.humbleui.skija.Image image=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(image==null&&System.nanoTime()<deadline){image=WallpaperImage.current(WallpaperStore.current());if(image==null)Thread.sleep(5);}
        assertNotNull(image,"actual asynchronous wallpaper decode must complete");
        return image;
    }
}
