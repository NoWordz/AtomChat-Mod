package com.atom.chat.render;

import com.atom.lib.client.render.MinecraftRenderBridge;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import org.lwjgl.opengl.GL11C;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

/** Uses the narrower texture scope when the installed AtomLib provides it. */
interface ItemIconTextures {
    Object ownerIdentity();
    void flush();
    Image borrow(int size, int texture, Runnable onRelease);

    static <T> T with(SkiaGraphics graphics, Function<ItemIconTextures, T> action) {
        var extension = Scoped.ACCESS;
        if (extension == null) return graphics.withContext(context -> action.apply(new Legacy(context)));
        Function<Object, T> callback = resources -> action.apply(new Scoped(extension, resources));
        return invoke(extension.scope(), MinecraftRenderBridge.get(), callback);
    }

    record Legacy(DirectContext context) implements ItemIconTextures {
        @Override public Object ownerIdentity() { return context; }

        @Override public void flush() {
            context.resetGLAll();
            context.flushAndSubmit(false);
        }

        @Override public Image borrow(int size, int texture, Runnable onRelease) {
            try (BackendTexture backend = BackendTexture.makeGL(size, size, false,
                    new GLTextureInfo(GL11C.GL_TEXTURE_2D, texture, GL11C.GL_RGBA8))) {
                return Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_8888, ColorAlphaType.PREMUL, null, onRelease);
            }
        }
    }

    record Scoped(Methods methods, Object lease) implements ItemIconTextures {
        private static final Methods ACCESS = findMethods();

        @Override public Object ownerIdentity() { return invoke(methods.identity(), lease); }
        @Override public void flush() { invoke(methods.flush(), lease); }
        @Override public Image borrow(int size, int texture, Runnable onRelease) {
            return invoke(methods.borrow(), lease, size, size, texture, onRelease);
        }

        private static Methods findMethods() {
            try {
                Class<?> bridge = MinecraftRenderBridge.class;
                Method scope = bridge.getMethod("withTextureResources", Function.class);
                Class<?> resources = Class.forName("com.atom.lib.client.render.SkijaRenderBridge$TextureResources",
                        false, bridge.getClassLoader());
                return new Methods(scope, resources.getMethod("ownerIdentity"), resources.getMethod("flush"),
                        resources.getMethod("borrowRgba8Texture", int.class, int.class, int.class, Runnable.class));
            } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
                return null;
            }
        }
    }

    record Methods(Method scope, Method identity, Method flush, Method borrow) {}

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object receiver, Object... arguments) {
        try {
            return (T) method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
