package com.atom.chat.reaction;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import static org.junit.jupiter.api.Assertions.*;

class ReactionMixinConfigurationTest {
    @Test void configuredMixinNamesResolveToPackagedClassesWithoutStartingMinecraft() throws Exception {
        ClassLoader loader=getClass().getClassLoader();
        var names=new LinkedHashSet<String>();
        if(loader.getResource("fabric.mod.json")!=null) {
            try(var stream=loader.getResourceAsStream("fabric.mod.json")) {
                var metadata=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                for(var item:metadata.getAsJsonArray("mixins")) names.add(item.isJsonPrimitive()?item.getAsString():item.getAsJsonObject().get("config").getAsString());
            }
        } else { names.add("atomchat.screen.mixins.json");names.add("atomchat.client.mixins.json"); }
        for(String name:names) {
            try(var stream=loader.getResourceAsStream(name)) {
                assertNotNull(stream,name);
                var config=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                String prefix=config.get("package").getAsString()+".";
                for(String list:new String[]{"mixins","client","server"}) {
                    if(!config.has(list)) continue;
                    for(var mixin:config.getAsJsonArray(list)) {
                        String path=(prefix+mixin.getAsString()).replace('.','/')+".class";
                        assertNotNull(loader.getResource(path),name+" references missing class "+path);
                    }
                }
            }
        }
    }
}
