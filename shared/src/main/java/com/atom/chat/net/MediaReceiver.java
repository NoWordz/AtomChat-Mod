package com.atom.chat.net;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Preserve the decoded packet's transport identity across the loader's client-thread hop. */
public final class MediaReceiver {
    private MediaReceiver() {}

    public static void enqueue(Object origin, MediaUploadScheduler session, Consumer<Runnable> enqueue,
                               Supplier<Object> activeTransport, Supplier<Object> currentTransport, Runnable receiver) {
        long epoch = session.connectionEpoch();
        enqueue.accept(() -> session.runIfCurrent(epoch, () -> {
            if (origin != null && origin == activeTransport.get() && origin == currentTransport.get()) receiver.run();
        }));
    }
}
