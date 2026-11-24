package com.atom.chat.chat;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import com.atom.chat.text.RichText;
public final class NativeMessageBridge {
    private NativeMessageBridge(){}
    public static int tick(){var client=Minecraft.getInstance();return client==null || client.gui==null?0:client.gui.getGuiTicks();}
    public static NativeMessageMeta capture(MessageSignature signature,GuiMessageTag tag){
        return new NativeMessageMeta(signature==null?null:NativeMessageMeta.signature(signature.bytes()),true,tag(tag),false,tick(),NativeMessageSync.session());
    }
    public static NativeMessageMeta.Tag tag(GuiMessageTag tag){
        if(tag==null)return null;
        var icon=tag.icon();
        NativeMessageMeta.Icon descriptor=icon==null?null:new NativeMessageMeta.Icon(icon.name(),"minecraft:textures/gui/chat_tags.png",false,icon.u,icon.v,icon.width,icon.height,32,32);
        return new NativeMessageMeta.Tag(tag.indicatorColor(),descriptor,
                com.atom.chat.history.ChatHistory.encodeNative(tag.text()),tag.text()==null?null:tag.text().getString(),tag.logTag());
    }
    public static net.minecraft.network.chat.Style hoverStyle(NativeMessageMeta.Tag tag){
        var text=tooltip(tag);
        return text==null?net.minecraft.network.chat.Style.EMPTY:net.minecraft.network.chat.Style.EMPTY.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,text));
    }
    public static RichText deletedMarker(){return RichText.of(Component.translatable("chat.deleted_marker").withStyle(net.minecraft.ChatFormatting.GRAY,net.minecraft.ChatFormatting.ITALIC));}
    public static Component tooltip(NativeMessageMeta.Tag tag){
        if(tag==null)return null;
        var text=com.atom.chat.history.ChatHistory.decodeNative(tag.tooltipJson());
        return text!=null?text:tag.tooltipText()==null?null:Component.literal(tag.tooltipText());
    }
    public static GuiMessageTag.Icon icon(NativeMessageMeta.Icon descriptor){
        if(descriptor==null)return null;
        for(var icon:GuiMessageTag.Icon.values())if(icon.name().equals(descriptor.name()))return icon;
        return null;
    }
}
