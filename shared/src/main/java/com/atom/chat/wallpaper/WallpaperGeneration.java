package com.atom.chat.wallpaper;

import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** One running render and one replaceable pending request; final publish is serialized with cancel. */
final class WallpaperGeneration {
    private final Object lock;
    private final Executor executor;
    private final Function<String, byte[]> render;
    private final BiPredicate<String, byte[]> publish;
    private final Consumer<RuntimeException> failure;
    private String pending;
    private boolean draining;
    private long generation;

    WallpaperGeneration(Object lock, Executor executor, Function<String, byte[]> render,
                        BiPredicate<String, byte[]> publish, Consumer<RuntimeException> failure) {
        this.lock=lock;this.executor=executor;this.render=render;this.publish=publish;this.failure=failure;
    }
    void request(String id) {
        synchronized(lock) {
            pending=id;generation++;
            if(!draining){draining=true;executor.execute(this::drain);}
        }
    }
    void cancel() { synchronized(lock){generation++;pending=null;} }
    private void drain() {
        while(true) {
            String id;long token;
            synchronized(lock){
                id=pending;pending=null;token=generation;
                if(id==null){draining=false;return;}
            }
            try {
                byte[] png=render.apply(id);
                synchronized(lock){if(token==generation)publish.test(id,png);}
            } catch(RuntimeException error) {
                synchronized(lock){if(token==generation)failure.accept(error);}
            }
        }
    }
}
