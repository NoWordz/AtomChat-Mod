package com.atom.chat.render;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Offline Minecraft loads only its base language; temporarily add the real mod locale for visual fixtures. */
public final class PreviewLanguage implements AutoCloseable {
    private final Object language;
    private final Field translations;
    private final Object original;

    private PreviewLanguage(Object language, Field translations, Object original) {
        this.language=language;this.translations=translations;this.original=original;
    }

    public static PreviewLanguage install(String locale) throws Exception {
        Class<?> type;
        try { type=Class.forName("net.minecraft.locale.Language"); }
        catch(ClassNotFoundException yarn) { type=Class.forName("net.minecraft.util.Language"); }
        Object language=type.getMethod("getInstance").invoke(null);
        for(Class<?> current=language.getClass();current!=null;current=current.getSuperclass()) {
            for(Field field:current.getDeclaredFields()) {
                if(!Map.class.isAssignableFrom(field.getType()) || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object original=field.get(language);
                Map<String,String> merged=new HashMap<>();
                if(original instanceof Map<?,?> map) map.forEach((k,v)->merged.put(k.toString(),v.toString()));
                try(var stream=PreviewLanguage.class.getResourceAsStream("/assets/atomchat/lang/"+locale+".json")) {
                    if(stream==null) throw new IllegalStateException("Missing actual locale: "+locale);
                    var object=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    object.entrySet().forEach(entry->merged.put(entry.getKey(),entry.getValue().getAsString()));
                }
                field.set(language,merged);
                return new PreviewLanguage(language,field,original);
            }
        }
        throw new IllegalStateException("No translation table in "+language.getClass());
    }

    @Override public void close() throws Exception { translations.set(language,original); }
}
