package com.atom.chat.history;
import com.atom.chat.chat.NativeMessageMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class HistoryRedactionRaceTest {
    @TempDir Path directory;
    private static HistoryStore.Entry original(){byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)7);return new HistoryStore.Entry(10,false,null,false,false,"quoted","secret",null,"Alice","Alice","secret","{\"text\":\"secret\"}",null,null,new NativeMessageMeta(NativeMessageMeta.signature(bytes),true,new NativeMessageMeta.Tag(3,null,"{\"text\":\"secret\"}","secret","Modified"),false,0,1));}
    private static final class Main implements java.util.concurrent.Executor {
        final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();public void execute(Runnable action){tasks.add(action);}void drain(){for(Runnable action;(action=tasks.poll())!=null;)action.run();}
    }
    @Test void postedLoadAndItsSaveBaseAreCleanedBeforePublicationAndReconnect()throws Exception {
        Path file=directory.resolve("history.jsonl");var old=original();assertTrue(HistoryFiles.write(file,List.of(old),()->true));
        var io=new HistoryLifecycleTest.QueueExecutor();var main=new Main();var loaded=new ArrayList<HistoryStore.Entry>();var errors=new ArrayList<Throwable>();
        var storage=new HistoryLifecycle.Storage(){public HistoryFiles.Loaded read(String key)throws Exception{return HistoryFiles.read(file);}public void cleanup(String k,int d,HistoryLifecycle.CleanupPermit p){}public boolean write(String k,List<HistoryStore.Entry> e,BooleanSupplier v)throws Exception{return HistoryFiles.write(file,e,v);}public void delete(String k)throws Exception{Files.deleteIfExists(file);}};
        var lifecycle=new HistoryLifecycle(io,main,storage,()->true,()->"MP:a",errors::add);
        lifecycle.join("MP:a",30,loaded::addAll);io.runAll(); // the old load is posted, not yet published
        assertTrue(lifecycle.redact(List.of(HistoryStore.deleted(old))));
        main.drain();io.runAll();main.drain();
        assertFalse(loaded.isEmpty());assertTrue(loaded.stream().allMatch(e->e.nativeMeta.deleted()));assertFalse(Files.readString(file).contains("secret"));
        lifecycle.disconnect();loaded.clear();lifecycle.join("MP:a",30,loaded::addAll);io.runAll();main.drain();
        assertTrue(loaded.stream().allMatch(e->e.nativeMeta.deleted()));assertTrue(errors.isEmpty());
    }
    @Test void inFlightStagedWriteCannotPublishAnOlderBodyAfterImmediateRedaction()throws Exception {
        Path file=directory.resolve("race.jsonl");var old=original();assertTrue(HistoryFiles.write(file,List.of(old),()->true));
        var io=Executors.newSingleThreadExecutor();var main=new Main();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var cleaned=new CountDownLatch(1);var staleCommitted=new AtomicBoolean(false);var blocking=new AtomicBoolean(false);var checks=new AtomicInteger();var errors=new CopyOnWriteArrayList<Throwable>();
        var storage=new HistoryLifecycle.Storage(){
            public HistoryFiles.Loaded read(String key)throws Exception{return HistoryFiles.read(file);}
            public void cleanup(String k,int d,HistoryLifecycle.CleanupPermit p){}
            public boolean write(String key,List<HistoryStore.Entry> entries,BooleanSupplier valid)throws Exception {
                boolean result=HistoryFiles.write(file,entries,()->{
                    if(blocking.get() && !entries.get(0).nativeMeta.deleted() && checks.incrementAndGet()==3){entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}
                    return valid.getAsBoolean();
                });
                if(result && entries.stream().anyMatch(e->!e.nativeMeta.deleted()))staleCommitted.set(true);
                if(result && entries.stream().allMatch(e->e.nativeMeta.deleted()))cleaned.countDown();return result;
            }
            public void delete(String key)throws Exception{Files.deleteIfExists(file);}
        };
        try {
            var lifecycle=new HistoryLifecycle(io,main,storage,()->true,()->"MP:a",errors::add);
            lifecycle.join("MP:a",30,e->{});io.submit(()->{}).get(10,TimeUnit.SECONDS);main.drain();blocking.set(true);
            assertTrue(lifecycle.save(List.of(old)));assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertTrue(lifecycle.redact(List.of(HistoryStore.deleted(old))));release.countDown();assertTrue(cleaned.await(10,TimeUnit.SECONDS));io.submit(()->{}).get(10,TimeUnit.SECONDS);main.drain();
            assertFalse(staleCommitted.get(),"the older staged write must fail its final validity check");assertFalse(Files.readString(file).contains("secret"));assertTrue(HistoryFiles.read(file).entries().stream().allMatch(e->e.nativeMeta.deleted()));assertTrue(errors.isEmpty());
            try(var paths=Files.list(directory)){assertFalse(paths.anyMatch(p->p.getFileName().toString().endsWith(".tmp")));}
        } finally {release.countDown();io.shutdownNow();assertTrue(io.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void clearAndNewEpochCannotRepublishQueuedOriginalsOrRedactionWrites()throws Exception {
        var io=new HistoryLifecycleTest.QueueExecutor();var main=new Main();var files=new HashMap<String,List<HistoryStore.Entry>>();files.put("MP:a",List.of(original()));var loaded=new ArrayList<HistoryStore.Entry>();var errors=new ArrayList<Throwable>();
        var storage=new HistoryLifecycle.Storage(){public HistoryFiles.Loaded read(String k){return new HistoryFiles.Loaded(files.containsKey(k),files.getOrDefault(k,List.of()));}public void cleanup(String k,int d,HistoryLifecycle.CleanupPermit p){}public boolean write(String k,List<HistoryStore.Entry> e,BooleanSupplier v){if(!v.getAsBoolean())return false;files.put(k,e);return true;}public void delete(String k){files.remove(k);}};
        var lifecycle=new HistoryLifecycle(io,main,storage,()->true,()->"MP:a",errors::add);
        lifecycle.join("MP:a",30,loaded::addAll);io.runAll();lifecycle.redact(List.of(HistoryStore.deleted(original())));lifecycle.clear();lifecycle.disconnect();io.runAll();main.drain();assertFalse(files.containsKey("MP:a"));assertTrue(loaded.isEmpty());
        lifecycle.join("MP:a",30,loaded::addAll);io.runAll();main.drain();assertTrue(loaded.isEmpty());assertTrue(errors.isEmpty());
    }
    @Test void disabledHistoryRedactsSavedRowsWithoutRecordingLiveMessagesOrCreatingAFile() throws Exception {
        Path file=directory.resolve("disabled.jsonl");var old=original();
        assertTrue(HistoryFiles.write(file,List.of(old),()->true));
        var io=new HistoryLifecycleTest.QueueExecutor();var main=new Main();var errors=new ArrayList<Throwable>();
        var enabled=new AtomicBoolean(true);
        var storage=new HistoryLifecycle.Storage(){
            public HistoryFiles.Loaded read(String key)throws Exception{return HistoryFiles.read(file);}
            public void cleanup(String key,int days,HistoryLifecycle.CleanupPermit permit){}
            public boolean write(String key,List<HistoryStore.Entry> entries,BooleanSupplier valid)throws Exception{return HistoryFiles.write(file,entries,valid);}
            public void delete(String key)throws Exception{Files.deleteIfExists(file);}
        };
        var lifecycle=new HistoryLifecycle(io,main,storage,enabled::get,()->"MP:a",errors::add);
        lifecycle.join("MP:a",30,entries->{});io.runAll();main.drain();enabled.set(false);
        assertFalse(lifecycle.save(List.of(HistoryFilesTest.entry(99))));
        assertTrue(lifecycle.redact(List.of(HistoryStore.deleted(old),HistoryFilesTest.entry(99))));
        io.runAll();main.drain();
        var saved=HistoryFiles.read(file).entries();assertEquals(1,saved.size());assertTrue(saved.get(0).nativeMeta.deleted());
        assertFalse(Files.readString(file).contains("secret"));
        Files.delete(file);
        assertTrue(lifecycle.redact(List.of(HistoryStore.deleted(old),HistoryFilesTest.entry(99))));
        io.runAll();main.drain();assertFalse(Files.exists(file));assertTrue(errors.isEmpty());
        assertTrue(HistoryFiles.write(file,List.of(old),()->true));
        // The exact deleted signature may lie outside the current global snapshot's private-chat priority.
        assertTrue(lifecycle.redact(List.of(HistoryFilesTest.entry(99)),old.nativeMeta.signature()));
        io.runAll();main.drain();assertTrue(HistoryFiles.read(file).entries().get(0).nativeMeta.deleted());
        assertFalse(Files.readString(file).contains("secret"));assertTrue(errors.isEmpty());
        byte[] secondBytes=new byte[256];Arrays.fill(secondBytes,(byte)9);
        var second=new HistoryStore.Entry(11,false,null,false,false,null,null,null,"Bob","Bob","secret B","{\"text\":\"secret B\"}",null,null,
                new NativeMessageMeta(NativeMessageMeta.signature(secondBytes),true,null,false,0,1));
        assertTrue(HistoryFiles.write(file,List.of(old,second),()->true));
        assertTrue(lifecycle.redact(List.of(HistoryFilesTest.entry(99)),old.nativeMeta.signature()));
        assertTrue(lifecycle.redact(List.of(HistoryFilesTest.entry(99)),second.nativeMeta.signature()));
        io.runAll();main.drain();
        var both=HistoryFiles.read(file).entries();assertEquals(2,both.size());
        assertTrue(both.stream().allMatch(entry->entry.nativeMeta.deleted()),"coalesced redactions must retain both exact signatures");
        assertFalse(Files.readString(file).contains("secret"));assertTrue(errors.isEmpty());
        var pending=HistoryLifecycle.class.getDeclaredField("pendingRedactions");pending.setAccessible(true);
        assertTrue(((Map<?,?>)pending.get(lifecycle)).isEmpty(),"completed disk batches must release their signature targets");
    }
}
