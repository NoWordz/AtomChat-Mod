package com.atom.chat.emote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteStoreTest {
    @TempDir
    Path tmp;

    private EmoteStore newStore() {
        return new EmoteStore(tmp.resolve("emotes"));
    }

    private File touch(Path parent, String name, byte[] bytes) throws IOException {
        Files.createDirectories(parent);
        Path p = parent.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    @Test
    void emptyDirYieldsEmptyList() {
        assertEquals(0, newStore().count());
    }

    @Test
    void isSupportedName() {
        assertTrue(EmoteStore.isSupportedName("a.png"));
        assertTrue(EmoteStore.isSupportedName("A.JPG"));
        assertTrue(EmoteStore.isSupportedName("b.jpeg"));
        assertTrue(EmoteStore.isSupportedName("c.gif"));
        assertFalse(EmoteStore.isSupportedName("d.webp"));
        assertFalse(EmoteStore.isSupportedName("e.bmp"));
        assertFalse(EmoteStore.isSupportedName("readme.txt"));
        assertFalse(EmoteStore.isSupportedName(null));
        assertFalse(EmoteStore.isSupportedName(""));
    }

    @Test
    void addCopiesSourceIntoDirAndKeepsSource() throws IOException {
        EmoteStore store = newStore();
        File source = touch(tmp, "cat.png", "png-bytes".getBytes(StandardCharsets.UTF_8));

        assertTrue(store.add(source));
        assertTrue(source.isFile(), "add must copy, not move");
        assertTrue(Files.exists(store.dir().resolve("cat.png")), "file copied into emote dir");
        assertEquals(1, store.count());
    }

    @Test
    void rejectsUnsupportedExtensions() throws IOException {
        EmoteStore store = newStore();
        File webp = touch(tmp, "anim.webp", "webp-bytes".getBytes(StandardCharsets.UTF_8));
        File txt = touch(tmp, "notes.txt", "hi".getBytes(StandardCharsets.UTF_8));

        assertFalse(store.add(webp));
        assertFalse(store.add(txt));
        assertEquals(0, store.count());
    }

    @Test
    void acceptsAnimatedGif() throws IOException {
        EmoteStore store = newStore();
        File gif = touch(tmp, "anim.gif", "gif-bytes".getBytes(StandardCharsets.UTF_8));

        assertTrue(store.add(gif));
        assertEquals(1, store.count());
        assertEquals("anim.gif", store.list().get(0).getName());
    }

    @Test
    void capsAtTheConfiguredMaximum() throws IOException {
        EmoteStore store = new EmoteStore(tmp.resolve("emotes"), () -> 3);
        File source = touch(tmp, "src.png", "x".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 3; i++) {
            File f = touch(tmp, "s" + i + ".png", "x".getBytes(StandardCharsets.UTF_8));
            assertTrue(store.add(f), "add #" + i + " should succeed");
        }
        assertEquals(3, store.count());
        assertTrue(store.isFull());
        assertFalse(store.add(source), "add beyond the cap must fail");
        assertEquals(3, store.count());
    }

    @Test
    void defaultStoreKeepsEverySticker() throws IOException {
        EmoteStore store = newStore();
        for (int i = 0; i < 30; i++) {
            assertTrue(store.add(touch(tmp, String.format("k%02d.png", i), "x".getBytes(StandardCharsets.UTF_8))),
                    "no cap by default, add #" + i + " should succeed");
        }
        assertEquals(30, store.count());
        assertFalse(store.isFull());
    }

    @Test
    void aNegativeCapCountsAsUnlimited() throws IOException {
        EmoteStore store = new EmoteStore(tmp.resolve("emotes"), () -> -5);
        for (int i = 0; i < 25; i++) {
            touch(store.dir(), String.format("n%02d.png", i), "x".getBytes(StandardCharsets.UTF_8));
        }
        store.refresh();
        assertEquals(25, store.count());
        assertFalse(store.isFull());
    }

    @Test
    void aRaisedCapAppliesOnTheNextRefresh() throws IOException {
        java.util.concurrent.atomic.AtomicInteger cap = new java.util.concurrent.atomic.AtomicInteger(2);
        EmoteStore store = new EmoteStore(tmp.resolve("emotes"), cap::get);
        for (int i = 0; i < 5; i++) {
            touch(store.dir(), String.format("r%02d.png", i), "x".getBytes(StandardCharsets.UTF_8));
        }
        store.refresh();
        assertEquals(2, store.count());
        assertTrue(store.isFull());

        cap.set(4);
        store.refresh();
        assertEquals(4, store.count());
        assertTrue(store.isFull());

        cap.set(2);
        store.refresh();
        assertEquals(2, store.count(), "a lowered cap also applies on the next refresh");
        assertTrue(store.isFull());
    }

    @Test
    void listsSortedByName() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "zeta.png", "x".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "alpha.png", "x".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "mid.png", "x".getBytes(StandardCharsets.UTF_8)));

        List<File> files = store.list();
        assertEquals(List.of("alpha.png", "mid.png", "zeta.png"),
                files.stream().map(File::getName).toList());
    }

    @Test
    void duplicateNameOverwrites() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "same.png", "first".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "same.png", "second".getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, store.count());
        try {
            String content = Files.readString(store.dir().resolve("same.png"));
            assertEquals("second", content, "duplicate name must overwrite");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void removeDeletesFileAndRefreshes() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "gone.png", "x".getBytes(StandardCharsets.UTF_8)));
        File emote = store.list().get(0);

        assertTrue(store.remove(emote));
        assertEquals(0, store.count());
        assertFalse(Files.exists(store.dir().resolve("gone.png")));
    }

    @Test
    void refusesToRemoveFilesOutsideTheDir() throws IOException {
        EmoteStore store = newStore();
        File outside = touch(tmp, "outside.png", "x".getBytes(StandardCharsets.UTF_8));

        assertFalse(store.remove(outside));
        assertTrue(outside.isFile());
        assertEquals(0, store.count());
    }

    @Test
    void scansExternallyDroppedFilesOnRefresh() throws IOException {
        EmoteStore store = newStore();
        touch(store.dir(), "manual.png", "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, store.count(), "list is cached until refresh");
        store.refresh();
        assertEquals(1, store.count());
        assertEquals("manual.png", store.list().get(0).getName());
    }

    @Test
    void externalFilesAreTrimmedToTheLocalCap() throws IOException {
        EmoteStore store = new EmoteStore(tmp.resolve("emotes"), () -> 5);
        for (int i = 0; i < 8; i++) {
            touch(store.dir(), String.format("l%02d.png", i), "x".getBytes(StandardCharsets.UTF_8));
        }
        store.refresh();

        assertEquals(5, store.count(), "the scan keeps the first 5 sorted names");
        assertTrue(store.isFull());
    }

    @Test
    void serverEmotesAreListedSeparatelyAndNeverTouchTheLocalCap() throws IOException {
        EmoteStore store = newStore();
        Path server = tmp.resolve("pack/emotes");
        for (int i = 0; i < 5; i++) {
            touch(server, String.format("s%02d.png", i), "x".getBytes(StandardCharsets.UTF_8));
        }
        touch(store.dir(), "mine.png", "x".getBytes(StandardCharsets.UTF_8));
        store.refresh();

        store.setServerDir(server);

        assertEquals(1, store.count(), "the server source is not part of the local list");
        assertEquals(5, store.serverCount());
        assertEquals("s00.png", store.serverList().get(0).getName());
        assertFalse(store.isFull(), "no local cap by default");
    }

    @Test
    void serverEmotesAreCappedAtServerMax() throws IOException {
        EmoteStore store = newStore();
        Path server = tmp.resolve("pack/emotes");
        for (int i = 0; i < EmoteStore.SERVER_MAX + 6; i++) {
            touch(server, String.format("s%02d.png", i), "x".getBytes(StandardCharsets.UTF_8));
        }

        store.setServerDir(server);

        assertEquals(EmoteStore.SERVER_MAX, store.serverCount());
    }

    @Test
    void aServerEmoteCannotBeDeletedThroughThePanel() throws IOException {
        EmoteStore store = newStore();
        Path server = tmp.resolve("pack/emotes");
        File theirs = touch(server, "theirs.png", "x".getBytes(StandardCharsets.UTF_8));
        store.setServerDir(server);

        assertFalse(store.remove(theirs), "the server folder is read-only");
        assertTrue(theirs.exists());
        assertEquals(1, store.serverCount());
    }

    @Test
    void clearingTheServerSourceLeavesTheLocalEmotesAlone() throws IOException {
        EmoteStore store = newStore();
        touch(store.dir(), "mine.png", "x".getBytes(StandardCharsets.UTF_8));
        store.setServerDir(tmp.resolve("pack/emotes"));

        store.setServerDir(null);

        assertEquals(1, store.count());
        assertEquals(0, store.serverCount());
    }
}
