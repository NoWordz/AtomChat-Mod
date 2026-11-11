package com.atom.chat.wallpaper;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class WallpaperGenerationTest {
    private Object queue(Object lock,Executor executor,Function<String,byte[]> render,
                         BiPredicate<String,byte[]> publish) throws Exception {
        var c=Class.forName("com.atom.chat.wallpaper.WallpaperGeneration")
                .getDeclaredConstructor(Object.class,Executor.class,Function.class,BiPredicate.class,Consumer.class);
        c.setAccessible(true);
        return c.newInstance(lock,executor,render,publish,(Consumer<RuntimeException>) e -> fail(e));
    }
    private void request(Object q,String id) throws Exception { var m=q.getClass().getDeclaredMethod("request",String.class);m.setAccessible(true);m.invoke(q,id); }
    private void cancel(Object q) throws Exception { var m=q.getClass().getDeclaredMethod("cancel");m.setAccessible(true);m.invoke(q); }
    @Test void aThousandQueuedSelectionsRenderOnlyTheNewestOne() throws Exception {
        List<Runnable> work=new ArrayList<>(); List<String> rendered=new ArrayList<>(),published=new ArrayList<>();
        Object lock=new Object();
        Object q=queue(lock,work::add,id->{rendered.add(id);return new byte[]{1};},(id,png)->{
            assertTrue(Thread.holdsLock(lock),"publish and cancellation must share a lock");published.add(id);return true;
        });
        for(int i=0;i<1000;i++) request(q,"preset-"+i);
        assertEquals(1,work.size(),"a burst reserves one worker task, never a thousand stale renders");
        work.remove(0).run();
        assertEquals(List.of("preset-999"),rendered);
        assertEquals(rendered,published);
    }
    @Test void clearBeforeWorkerStartsDoesNotRenderOrResurrectWallpaper() throws Exception {
        List<Runnable> work=new ArrayList<>();List<String> rendered=new ArrayList<>(),published=new ArrayList<>();
        Object q=queue(new Object(),work::add,id->{rendered.add(id);return new byte[]{1};},(id,png)->{published.add(id);return true;});
        request(q,"fluid");cancel(q);work.remove(0).run();
        assertTrue(rendered.isEmpty());assertTrue(published.isEmpty());
    }
    @Test void replacementDuringRenderingNeverPublishesTheStaleImage() throws Exception {
        List<Runnable> work=new ArrayList<>();List<String> rendered=new ArrayList<>(),published=new ArrayList<>();Object[] holder=new Object[1];
        holder[0]=queue(new Object(),work::add,id->{rendered.add(id);if(id.equals("old")){
            try {request(holder[0],"new");} catch(Exception e){throw new RuntimeException(e);}
        } return new byte[]{1};},(id,png)->{published.add(id);return true;});
        request(holder[0],"old");work.remove(0).run();
        assertEquals(List.of("old","new"),rendered);
        assertEquals(List.of("new"),published);
    }
    @Test void clearDuringRenderingCannotPublishAfterTheClear() throws Exception {
        List<Runnable> work=new ArrayList<>();List<String> published=new ArrayList<>();Object[] holder=new Object[1];
        holder[0]=queue(new Object(),work::add,id->{try{cancel(holder[0]);}catch(Exception e){throw new RuntimeException(e);}return new byte[]{1};},
                (id,png)->{published.add(id);return true;});
        request(holder[0],"fluid");work.remove(0).run();assertTrue(published.isEmpty());
    }
}
