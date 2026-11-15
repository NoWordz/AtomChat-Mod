package com.atom.chat.reaction;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ReactionTransportTest {
    @Test void concurrentSendsKeepEachReceiptAdjacentToItsMessage() throws Exception {
        Object connection=new Object();
        List<String> wire=Collections.synchronizedList(new ArrayList<>());
        var start=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(4);
        try {
            var futures=new ArrayList<Future<?>>();
            for(int worker=0;worker<4;worker++) {
                int sender=worker;
                futures.add(workers.submit(()->{
                    try { start.await(); } catch(InterruptedException e) { throw new RuntimeException(e); }
                    for(int n=0;n<100;n++) {
                        String id=sender+":"+n;
                        ReactionTransport.sendPair(connection,()->{wire.add("R"+id);Thread.yield();},()->wire.add("M"+id));
                    }
                }));
            }
            start.countDown();for(var future:futures) future.get(10,TimeUnit.SECONDS);
            assertEquals(800,wire.size());
            for(int n=0;n<wire.size();n+=2) assertEquals("M"+wire.get(n).substring(1),wire.get(n+1));
        } finally { workers.shutdownNow(); }
    }
}
