package com.atom.chat.font;

import io.github.humbleui.skija.Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real FontManager and FontSource with unavailable/corrupt resources. */
class FontFallbackTest {
    @Test
    void missingAndUndecodableFontsFallBackForRegularAndBold() throws Exception {
        for (boolean corrupt : new boolean[]{false, true}) {
            var loader = new FontLoader(corrupt);
            Class<?> manager = loader.loadClass("com.atom.chat.font.FontManager");
            Font regular = (Font) manager.getMethod("font", float.class).invoke(null, 21F);
            Font bold = (Font) manager.getMethod("boldFont", float.class).invoke(null, 21F);
            assertNotNull(regular.getTypeface(), "system face: corrupt=" + corrupt);
            assertTrue(regular.isEmboldened(), "regular fallback: corrupt=" + corrupt);
            assertNotNull(bold.getTypeface(), "bold face: corrupt=" + corrupt);
            assertTrue(bold.isEmboldened(), "bold fallback: corrupt=" + corrupt);
        }
    }

    @Test
    void oldAtomLibWithoutResourceMethodFallsBackForBothFaces() throws Exception {
        var loader = new FontLoader(false, true);
        Class<?> manager = loader.loadClass("com.atom.chat.font.FontManager");
        Font regular = (Font) manager.getMethod("font", float.class).invoke(null, 21F);
        Font bold = (Font) manager.getMethod("boldFont", float.class).invoke(null, 21F);
        assertNotNull(regular.getTypeface());
        assertTrue(regular.isEmboldened());
        assertNotNull(bold.getTypeface());
        assertTrue(bold.isEmboldened());
    }

    private static final class FontLoader extends ClassLoader {
        private final boolean corrupt;
        private final boolean oldApi;
        FontLoader(boolean corrupt) { this(corrupt, false); }
        FontLoader(boolean corrupt, boolean oldApi) { super(FontFallbackTest.class.getClassLoader()); this.corrupt = corrupt; this.oldApi = oldApi; }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals("com.atom.chat.font.FontManager") && !name.equals("com.atom.lib.font.FontSource"))
                return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream input = getParent().getResourceAsStream(oldApi && name.equals("com.atom.lib.font.FontSource")
                            ? "fixtures/atomlib-old-font-source.class" : name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        @Override public URL getResource(String name) {
            if (name.startsWith("com/atom/lib/fonts/bundled-default") && name.endsWith(".otf")) {
                if (!corrupt) return null;
                try {
                    return new URL(null, "test:corrupt", new URLStreamHandler() {
                        @Override protected URLConnection openConnection(URL url) {
                            return new URLConnection(url) {
                                @Override public void connect() { }
                                @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[]{1, 2, 3}); }
                            };
                        }
                    });
                } catch (java.net.MalformedURLException e) { throw new AssertionError(e); }
            }
            return super.getResource(name);
        }
    }
}
