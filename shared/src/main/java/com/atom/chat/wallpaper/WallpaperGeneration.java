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
    private record Request(String id, Executor publication) {}
    private Request pending;
    private boolean draining;
    private long generation;

    WallpaperGeneration(Object lock, Executor executor, Function<String, byte[]> render,
                        BiPredicate<String, byte[]> publish, Consumer<RuntimeException> failure) {
        this.lock=lock;this.executor=executor;this.render=render;this.publish=publish;this.failure=failure;
    }
    void request(String id) {
        request(id, Runnable::run);
    }
    void request(String id, Executor publication) {
        synchronized(lock) {
            pending = new Request(id, publication);generation++;
            if(!draining){draining=true;executor.execute(this::drain);}
        }
    }
    void cancel() { synchronized(lock){generation++;pending=null;} }
    private void drain() {
        while(true) {
            Request request;long token;
            synchronized(lock){
                request=pending;pending=null;token=generation;
                if(request==null){draining=false;return;}
            }
            try {
                byte[] png=render.apply(request.id());
                request.publication().execute(() -> {
                    synchronized (lock) {
                        if (token != generation) return;
                        try { publish.test(request.id(), png); }
                        catch (RuntimeException error) { failure.accept(error); }
                    }
                });
            } catch(RuntimeException error) {
                synchronized(lock){if(token==generation)failure.accept(error);}
            }
        }
    }
}
