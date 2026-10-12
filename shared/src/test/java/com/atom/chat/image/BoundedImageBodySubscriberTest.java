package com.atom.chat.image;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
class BoundedImageBodySubscriberTest {
    private static final Flow.Subscription SUBSCRIPTION=new Flow.Subscription() {
        public void request(long count) {} public void cancel() {}
    };
    @Test void returnsBytesAtTheExactLimit() {
        var reader=new BoundedImageBodySubscriber(4); reader.onSubscribe(SUBSCRIPTION);
        reader.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3,4}))); reader.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4},reader.getBody().toCompletableFuture().join());
    }
    @Test void rejectsOverflowAcrossChunksWithoutReturningPartialData() {
        var reader=new BoundedImageBodySubscriber(4); reader.onSubscribe(SUBSCRIPTION);
        reader.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3})));
        reader.onNext(List.of(ByteBuffer.wrap(new byte[]{4,5}))); reader.onComplete();
        assertThrows(CompletionException.class,()->reader.getBody().toCompletableFuture().join());
    }
    @Test void rejectsDeclaredOversizeBeforeCollecting() {
        var reader=new BoundedImageBodySubscriber(-1); reader.onSubscribe(SUBSCRIPTION);
        assertThrows(CompletionException.class,()->reader.getBody().toCompletableFuture().join());
    }
}
