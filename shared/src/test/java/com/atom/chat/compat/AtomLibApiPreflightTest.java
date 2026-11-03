package com.atom.chat.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

class AtomLibApiPreflightTest {
    private static final String CHECK = "com.atom.chat.compat.AtomLibDependencyCheck";
    private static final String CROP = "com.atom.lib.geometry.CropMath";

    @TempDir Path temporary;

    private final class IsolatedLoader extends URLClassLoader {
        final boolean missingCrop;
        final byte[] cropReplacement;
        int libLookups;

        IsolatedLoader(boolean missingCrop, byte[] cropReplacement) {
            this(missingCrop, cropReplacement, new URL[0]);
        }

        IsolatedLoader(boolean missingCrop, byte[] cropReplacement, URL[] urls) {
            super(urls, AtomLibDependencyCheck.class.getClassLoader());
            this.missingCrop = missingCrop;
            this.cropReplacement = cropReplacement;
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith("com.atom.lib.")) libLookups++;
                if (loaded == null && name.equals(CROP) && missingCrop) throw new ClassNotFoundException(name);
                if (loaded == null && name.equals(CROP) && cropReplacement != null) {
                    loaded = defineClass(name, cropReplacement, 0, cropReplacement.length);
                }
                if (loaded == null && name.equals(CHECK)) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (loaded == null && name.startsWith("com.atom.lib.") && getURLs().length > 0) {
                    // An explicitly supplied alternate jar must provide the class;
                    // never silently fall back to the compile-time AtomLib.
                    loaded = findClass(name);
                }
                if (loaded == null) loaded = super.loadClass(name, false);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    private static void require(IsolatedLoader loader, String version) throws Throwable {
        try {
            loader.loadClass(CHECK).getMethod("require", String.class).invoke(null, version);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    @Test void absentRequiredClassProducesAnExplicitDiagnostic() {
        var error = assertThrows(IllegalStateException.class, () -> require(new IsolatedLoader(true, null), "2.0.0"));
        assertTrue(error.getMessage().contains(CROP), error.getMessage());
        assertTrue(error.getMessage().contains("2.0.0"), error.getMessage());
    }

    @Test void missingRequiredMethodProducesAnExplicitDiagnostic() throws Exception {
        Path source = temporary.resolve("com/atom/lib/geometry/CropMath.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.atom.lib.geometry; public final class CropMath { "
                + "public static float baseScale(float a,float b,float c,float d) {return 1;} }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", temporary.toString(), source.toString()));
        byte[] replacement = Files.readAllBytes(temporary.resolve("com/atom/lib/geometry/CropMath.class"));
        var error = assertThrows(IllegalStateException.class, () -> require(new IsolatedLoader(false, replacement), "2.0.0"));
        assertTrue(error.getMessage().contains("clampScale"), error.getMessage());
        assertTrue(error.getMessage().contains(CROP), error.getMessage());
    }

    @Test void staticToInstanceChangeIsRejected() throws Exception {
        Path source = temporary.resolve("com/atom/lib/geometry/CropMath.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.atom.lib.geometry; public final class CropMath { "
                + "public float baseScale(float a,float b,float c,float d) {return 1;} }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", temporary.toString(), source.toString()));
        byte[] replacement = Files.readAllBytes(temporary.resolve("com/atom/lib/geometry/CropMath.class"));
        var error = assertThrows(IllegalStateException.class, () -> require(new IsolatedLoader(false, replacement), "2.0.0"));
        assertTrue(error.getMessage().contains("baseScale"), error.getMessage());
    }

    @Test void resolvingSymbolsDoesNotInitializeLibraryClasses() throws Exception {
        Path source = temporary.resolve("com/atom/lib/geometry/CropMath.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.atom.lib.geometry; public final class CropMath { "
                + "static { if (true) throw new AssertionError(\"must not initialize\"); } "
                + "public static float baseScale(float a,float b,float c,float d){return 0;} "
                + "public static float clampScale(float a,float b){return 0;} "
                + "public static float[] clampCover(float a,float b,float c,float d,float e,float f,float g,float h,float i){return null;} "
                + "public static float[] zoomAtCentre(float a,float b,float c,float d,float e,float f,float g,float h,float i,float j,float k){return null;} "
                + "public static float[] visibleRect(float a,float b,float c,float d,float e,float f,float g,float h,float i){return null;} }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", temporary.toString(), source.toString()));
        byte[] replacement = Files.readAllBytes(temporary.resolve("com/atom/lib/geometry/CropMath.class"));
        assertDoesNotThrow(() -> require(new IsolatedLoader(false, replacement), "2.0.0"));
    }

    @Test void loadingTheCheckerDoesNotLoadClientLibraryClasses() throws Exception {
        IsolatedLoader loader = new IsolatedLoader(true, null);
        Class.forName(CHECK, true, loader);
        assertEquals(0, loader.libLookups);
    }

    @Test void actualPinnedLibraryPassesWithDifferentVersionLabels() {
        for (String version : new String[]{"0.1.0-beta", "0.2.0", "2.0.0"}) {
            assertDoesNotThrow(() -> require(new IsolatedLoader(false, null), version));
        }
    }

    @Test void alternateLibraryJarPassesTheSameClientPreflight() throws Exception {
        String jar = System.getProperty("atomchat.test.alternateAtomLibJar");
        Assumptions.assumeTrue(jar != null && !jar.isBlank(), "alternate jar not supplied");
        try (IsolatedLoader loader = new IsolatedLoader(false, null, new URL[]{Path.of(jar).toUri().toURL()})) {
            assertDoesNotThrow(() -> require(loader, "0.2.0"));
        }
    }

    @Test void eachClientEntrypointChecksBeforeReadingClientConfiguration() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        assertNotNull(root, "repository root");
        for (String platform : new String[]{"1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric"}) {
            String source = Files.readString(root.resolve("platforms")
                    .resolve(platform).resolve("src/main/java/com/atom/chat/AtomChatClient.java"));
            int check = source.indexOf("AtomLibDependencyCheck.require");
            int config = source.indexOf("AtomChatConfig.get()");
            assertTrue(check >= 0, platform + " must check AtomLib from its client entrypoint");
            assertTrue(config > check, platform + " must check before reading client configuration");
        }

        String forgeMain = Files.readString(root.resolve("platforms/1.20.1-forge/src/main/java/com/atom/chat/AtomChat.java"));
        int clientGuard = forgeMain.indexOf("FMLEnvironment.dist.isClient()");
        int clientInit = forgeMain.indexOf("AtomChatClient.init(");
        assertTrue(clientGuard >= 0 && clientInit > clientGuard,
                "Forge must guard the client entrypoint for dedicated servers");
        String neoClient = Files.readString(root.resolve("platforms/1.21.1-neoforge/src/main/java/com/atom/chat/AtomChatClient.java"));
        assertTrue(neoClient.contains("@Mod(value = AtomChat.MOD_ID, dist = Dist.CLIENT)"),
                "NeoForge must keep the checking class client-only");
        String fabricMain = Files.readString(root.resolve("platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChat.java"));
        assertFalse(fabricMain.contains("AtomChatClient"),
                "Fabric's common initializer must not load its client entrypoint on a dedicated server");
        for (String platform : new String[]{"1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric"}) {
            String common = Files.readString(root.resolve("platforms")
                    .resolve(platform).resolve("src/main/java/com/atom/chat/AtomChat.java"));
            assertFalse(common.contains("AtomLibDependencyCheck.require"),
                    platform + " common code must not require the client-only AtomLib API");
        }
    }
}
