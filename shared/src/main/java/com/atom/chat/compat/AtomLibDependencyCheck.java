package com.atom.chat.compat;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Properties;

/** Client bootstrap check. No AtomLib/Skija/client types in this class's signatures. */
public final class AtomLibDependencyCheck {
    private AtomLibDependencyCheck() {}
    public static String requiredVersion() {
        try(var input=AtomLibDependencyCheck.class.getResourceAsStream("/atomchat-atomlib.properties")){
            if(input==null)throw new IllegalStateException("AtomChat is missing its AtomLib requirement metadata");
            var properties=new Properties();properties.load(input);String version=properties.getProperty("version");
            if(version==null||version.isBlank())throw new IllegalStateException("AtomChat has invalid AtomLib requirement metadata");
            return version;
        }catch(IOException error){throw new IllegalStateException("Cannot read AtomChat's AtomLib requirement",error);}
    }
    public static void require(String installedVersion){
        validate(installedVersion,requiredVersion(),System.getProperty("os.name"),System.getProperty("os.arch"));
        checkApi(installedVersion);
    }

    /** Resolve selected directly used symbols without initializing AtomLib or invoking rendering. */
    private static void checkApi(String installed) {
        ClassLoader loader = AtomLibDependencyCheck.class.getClassLoader();
        String symbol = "";
        try {
            // Check the pure helpers first, then the client rendering entry points.
            String crop = "com.atom.lib.geometry.CropMath";
            symbol = crop + ".baseScale(FFFF)F"; resolveMethod(loader, crop, "baseScale", "(FFFF)F", true);
            symbol = crop + ".clampScale(FF)F"; resolveMethod(loader, crop, "clampScale", "(FF)F", true);
            symbol = crop + ".clampCover(FFFFFFFFF)[F"; resolveMethod(loader, crop, "clampCover", "(FFFFFFFFF)[F", true);
            symbol = crop + ".zoomAtCentre(FFFFFFFFFFF)[F"; resolveMethod(loader, crop, "zoomAtCentre", "(FFFFFFFFFFF)[F", true);
            symbol = crop + ".visibleRect(FFFFFFFFF)[F"; resolveMethod(loader, crop, "visibleRect", "(FFFFFFFFF)[F", true);

            String design = "com.atom.lib.design.atomchat.Design";
            symbol = design + ".host(DesignHost)"; resolveMethod(loader, design, "host", "(Lcom/atom/lib/design/atomchat/DesignHost;)V", true);
            symbol = design + ".rippleSink(RippleSink)"; resolveMethod(loader, design, "rippleSink", "(Lcom/atom/lib/design/atomchat/Design$RippleSink;)V", true);
            String host = "com.atom.lib.design.atomchat.DesignHost";
            symbol = host + ".cornerRadius()"; resolveMethod(loader, host, "cornerRadius", "()F", false);
            symbol = host + ".accentColor()"; resolveMethod(loader, host, "accentColor", "()I", false);
            symbol = host + ".textSecondaryColor()"; resolveMethod(loader, host, "textSecondaryColor", "()I", false);
            symbol = host + ".cardTint()"; resolveMethod(loader, host, "cardTint", "()F", false);
            symbol = host + ".cardColor()"; resolveMethod(loader, host, "cardColor", "()I", false);
            symbol = host + ".panelBackgroundColor()"; resolveMethod(loader, host, "panelBackgroundColor", "()I", false);
            symbol = host + ".halfScreenMode()"; resolveMethod(loader, host, "halfScreenMode", "()Z", false);
            symbol = design + "$RippleSink.region(FFFFFI)V";
            resolveMethod(loader, "com.atom.lib.design.atomchat.Design$RippleSink", "region", "(FFFFFI)V", false);
            String tokens = "com.atom.lib.design.atomchat.UiTokens";
            symbol = tokens + ".s(F)F"; resolveMethod(loader, tokens, "s", "(F)F", true);

            String draw = "com.atom.lib.design.atomchat.SkiaDraw";
            symbol = draw + ".drawRoundedRect(Canvas,FFFFFI)V"; resolveMethod(loader, draw, "drawRoundedRect", "(Lio/github/humbleui/skija/Canvas;FFFFFI)V", true);
            symbol = draw + ".drawRoundedImage(Canvas,Image,FFFFF,SamplingMode)V"; resolveMethod(loader, draw, "drawRoundedImage", "(Lio/github/humbleui/skija/Canvas;Lio/github/humbleui/skija/Image;FFFFFLio/github/humbleui/skija/SamplingMode;)V", true);
            symbol = draw + ".clip(Canvas,FFFFF)V"; resolveMethod(loader, draw, "clip", "(Lio/github/humbleui/skija/Canvas;FFFFF)V", true);
            String cards = "com.atom.lib.design.atomchat.UiCards";
            symbol = cards + ".drawCard(Canvas,FFFFFF)V"; resolveMethod(loader, cards, "drawCard", "(Lio/github/humbleui/skija/Canvas;FFFFFF)V", true);
            String icons = "com.atom.lib.design.atomchat.AppIcons";
            symbol = icons + ".ICON_CHECK_PATH";
            MethodHandles.publicLookup().findStaticGetter(Class.forName(icons, false, loader), "ICON_CHECK_PATH",
                    Class.forName("io.github.humbleui.skija.Path", false, loader));

            String bridge = "com.atom.lib.client.render.MinecraftRenderBridge";
            symbol = bridge + ".get()"; resolveMethod(loader, bridge, "get", "()Lcom/atom/lib/client/render/MinecraftRenderBridge;", true);
            symbol = bridge + ".prepare()"; resolveMethod(loader, bridge, "prepare", "()V", false);
            symbol = bridge + ".drawFrame(BorrowedFramePainter)"; resolveMethod(loader, bridge, "drawFrame", "(Lcom/atom/lib/client/render/BorrowedFramePainter;)V", false);
            symbol = "BorrowedFramePainter.paint(Canvas,BorrowedFrame)V";
            resolveMethod(loader, "com.atom.lib.client.render.BorrowedFramePainter", "paint",
                    "(Lio/github/humbleui/skija/Canvas;Lcom/atom/lib/client/render/BorrowedFrame;)V", false);
            symbol = bridge + ".vanillaPass(Runnable)"; resolveMethod(loader, bridge, "vanillaPass", "(Ljava/lang/Runnable;)V", false);
            symbol = bridge + ".withContext(Function)"; resolveMethod(loader, bridge, "withContext", "(Ljava/util/function/Function;)Ljava/lang/Object;", false);
            String frame = "com.atom.lib.client.render.BorrowedFrame";
            symbol = frame + ".surface()"; resolveMethod(loader, frame, "surface", "()Lio/github/humbleui/skija/Surface;", false);
            symbol = frame + ".backendRenderTarget()"; resolveMethod(loader, frame, "backendRenderTarget", "()Lio/github/humbleui/skija/BackendRenderTarget;", false);
        } catch (ReflectiveOperationException | LinkageError | TypeNotPresentException error) {
            throw new IllegalStateException("AtomChat client cannot use installed AtomLib " + installed
                    + ": missing or incompatible required API " + symbol
                    + ". Install an AtomLib build for this Minecraft version and loader that provides it; "
                    + "AtomChat was compiled against " + requiredVersion() + ".", error);
        }
    }

    private static void resolveMethod(ClassLoader loader, String owner, String name, String descriptor, boolean isStatic)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(owner, false, loader);
        MethodType signature = MethodType.fromMethodDescriptorString(descriptor, loader);
        if (isStatic) MethodHandles.publicLookup().findStatic(type, name, signature);
        else MethodHandles.publicLookup().findVirtual(type, name, signature);
    }
    static void validate(String installed,String required,String os,String arch){
        if(installed==null||installed.isBlank())throw new IllegalStateException("AtomChat client requires AtomLib for the same Minecraft version and loader; installed: missing. This build was compiled against AtomLib "+required+". Dedicated servers do not need AtomLib.");
        String architecture=arch.toLowerCase(Locale.ROOT);
        if(!os.toLowerCase(Locale.ROOT).startsWith("windows")||!java.util.Set.of("amd64","x86_64","aarch64","arm64").contains(architecture))
            throw new IllegalStateException("This AtomLib test build supports Windows x64/ARM64 clients only; JVM platform is "+os+" / "+arch+". Previous Linux AtomChat artifacts are unchanged.");
    }
}
