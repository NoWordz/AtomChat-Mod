package com.atom.chat.image;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
/** Bounded streaming collector; HttpClient still waits for the body, preserving its request timeout. */
final class BoundedImageBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result=new CompletableFuture<>();
    private Flow.Subscription subscription;
    BoundedImageBodySubscriber(int limit) { this.limit=limit; }
    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription value) {
        if(subscription!=null) { value.cancel(); return; }
        subscription=value;
        if(limit<0) { value.cancel(); result.completeExceptionally(new IOException("image_source_bytes")); }
        else value.request(1);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if(result.isDone()) return;
        long count=bytes.size(); for(ByteBuffer buffer:buffers) count+=buffer.remaining();
        if(count>limit) { subscription.cancel(); result.completeExceptionally(new IOException("image_source_bytes")); return; }
        for(ByteBuffer buffer:buffers) {
            byte[] part=new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable error) { result.completeExceptionally(error); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
}
