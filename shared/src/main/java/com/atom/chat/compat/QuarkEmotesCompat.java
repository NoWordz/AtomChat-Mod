package com.atom.chat.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Hit protection for Quark's original emote renderer, which draws and handles
 * clicks itself rather than registering vanilla widgets. This never draws,
 * invokes a callback, or changes Quark's state or layout.
 */
public final class QuarkEmotesCompat {
    private static final QuarkEmotesCompat INSTANCE = new QuarkEmotesCompat(
            "org.violetmoon.quark.content.tweaks.module.EmotesModule$Client");

    private final String clientClassName;
    // ClassValue caches both success and failure without retaining mod class loaders.
    // Only matching renderer classes enter this cache; unrelated screens never resolve Quark.
    private final ClassValue<Access> accessors = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> rendererClass) {
            try {
                Class<?> client = Class.forName(clientClassName, false, rendererClass.getClassLoader());
                Field toggle = staticField(client, "emoteToggleButton");
                Field buttons = staticField(client, "emoteButtons");
                Field visible = staticField(client, "emotesVisible");
                if (!List.class.isAssignableFrom(buttons.getType()) || visible.getType() != boolean.class) {
                    return Access.UNAVAILABLE;
                }
                return new Access(toggle, buttons, visible, new Bounds(toggle.getType()));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
                return Access.UNAVAILABLE;
            }
        }
    };

    // Alternate owner name keeps schema tests independent of Minecraft and Quark jars.
    QuarkEmotesCompat(String clientClassName) {
        this.clientClassName = clientClassName;
    }

    /**
     * Returns true only over Quark buttons drawn by a renderer on this screen.
     * Call before outside-click dismissal, and keep rendering Quark's original
     * renderable so its draw-time mouse handling can receive the click.
     * Coordinates must use the same GUI coordinate space as Screen.renderables.
     */
    public static boolean hovered(Iterable<?> renderables, double mouseX, double mouseY) {
        return INSTANCE.isHovered(renderables, mouseX, mouseY);
    }

    boolean isHovered(Iterable<?> renderables, double mouseX, double mouseY) {
        if (renderables == null || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) {
            return false;
        }
        for (Object renderable : renderables) {
            if (renderable != null && renderable.getClass().getName().startsWith(clientClassName)
                    && accessors.get(renderable.getClass()).hovered(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException, IllegalAccessException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            Field field;
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException missing) {
                continue;
            }
            if (!field.trySetAccessible()) {
                throw new IllegalAccessException(name);
            }
            return field;
        }
        throw new NoSuchFieldException(name);
    }

    private static Field staticField(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = field(owner, name);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException(name);
        }
        return field;
    }

    private static final class Access {
        private static final Access UNAVAILABLE = new Access(null, null, null, null);

        private final Field toggle;
        private final Field buttons;
        private final Field visible;
        private final Bounds bounds;
        private boolean disabled;

        private Access(Field toggle, Field buttons, Field visible, Bounds bounds) {
            this.toggle = toggle;
            this.buttons = buttons;
            this.visible = visible;
            this.bounds = bounds;
            disabled = bounds == null;
        }

        private boolean hovered(double mouseX, double mouseY) {
            if (disabled) {
                return false;
            }
            try {
                // Cache fields, never instances: Quark rebuilds buttons during init/resize.
                if (bounds.contains(toggle.get(null), mouseX, mouseY)) {
                    return true;
                }
                if (visible.getBoolean(null) && buttons.get(null) instanceof List<?> currentButtons) {
                    for (Object button : currentButtons) {
                        if (bounds.contains(button, mouseX, mouseY)) {
                            return true;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
                // An incompatible schema/access failure must not throw again every frame.
                disabled = true;
            }
            return false;
        }
    }

    private static final class Bounds {
        private final Class<?> buttonType;
        private final Field x;
        private final Field y;
        private final Field width;
        private final Field height;

        private Bounds(Class<?> buttonType) throws ReflectiveOperationException {
            this.buttonType = buttonType;
            x = coordinate(buttonType, "x");
            y = coordinate(buttonType, "y");
            width = coordinate(buttonType, "width");
            height = coordinate(buttonType, "height");
        }

        private static Field coordinate(Class<?> type, String name) throws ReflectiveOperationException {
            Field field = field(type, name);
            if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) {
                throw new NoSuchFieldException(name);
            }
            return field;
        }

        private boolean contains(Object button, double mouseX, double mouseY) throws IllegalAccessException {
            if (!buttonType.isInstance(button)) {
                return false;
            }
            int left = x.getInt(button);
            int top = y.getInt(button);
            int w = width.getInt(button);
            int h = height.getInt(button);
            // Quark's NotButton.draw uses inclusive left/top, exclusive right/bottom.
            return w > 0 && h > 0 && mouseX >= left && mouseY >= top
                    && mouseX < (double) left + w && mouseY < (double) top + h;
        }
    }
}
