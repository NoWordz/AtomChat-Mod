package com.atom.chat.font;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Resource-level comparison; never compares rendered glyphs or pack names. */
public final class NativeFontResources {
    @FunctionalInterface public interface Resources { InputStream open(String id) throws IOException; }
    private NativeFontResources() {}

    public static String dependency(JsonObject definition) {
        String type = definition.get("type").getAsString();
        if (type.startsWith("minecraft:")) type = type.substring(10);
        String key = type.equals("unihex") ? "hex_file" : "file";
        if (!definition.has(key)) return null;
        String id = definition.get(key).getAsString();
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return namespace + ":" + (type.equals("bitmap") ? "textures/" : type.equals("ttf") ? "font/" : "") + path;
    }

    public static boolean changed(JsonObject definition, Set<JsonObject> baseline,
                                  Resources selected, Resources builtin) throws IOException {
        if (!baseline.contains(definition)) return true;
        String id = dependency(definition);
        if (id == null) return false;
        try (InputStream actual = selected.open(id); InputStream original = builtin.open(id)) {
            if (actual == null || original == null) return true;
            byte[] a = new byte[8192], b = new byte[8192];
            for (;;) {
                int na = actual.readNBytes(a, 0, a.length), nb = original.readNBytes(b, 0, b.length);
                if (na != nb) return true;
                for (int i = 0; i < na; i++) if (a[i] != b[i]) return true;
                if (na == 0) return false;
            }
        }
    }
}
