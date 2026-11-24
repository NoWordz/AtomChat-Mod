package com.atom.chat.render;
public final class NativeFontOptions {
    private NativeFontOptions(){}
    public static java.util.Map<String,Boolean> values(net.minecraft.client.Minecraft client){return client.options==null?java.util.Map.of():java.util.Map.of("uniform",client.options.forceUnicodeFont().get(),"jp",client.options.japaneseGlyphVariants().get());}
}
