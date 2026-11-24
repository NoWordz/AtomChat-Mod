package com.atom.chat.chat;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import com.atom.chat.text.RichText;
public final class NativeMessageBridge {
    private NativeMessageBridge(){}
    public static int tick(){var client=MinecraftClient.getInstance();return client==null || client.inGameHud==null?0:client.inGameHud.getTicks();}
    public static NativeMessageMeta capture(MessageSignatureData signature,MessageIndicator tag){
        return new NativeMessageMeta(signature==null?null:NativeMessageMeta.signature(signature.data()),true,tag(tag),false,tick(),NativeMessageSync.session());
    }
    public static NativeMessageMeta.Tag tag(MessageIndicator tag){
        if(tag==null)return null;
        var icon=tag.icon();
        NativeMessageMeta.Icon descriptor=icon==null?null:new NativeMessageMeta.Icon(icon.name(),icon.texture.toString(),true,0,0,icon.width,icon.height,icon.width,icon.height);
        return new NativeMessageMeta.Tag(tag.indicatorColor(),descriptor,
                com.atom.chat.history.ChatHistory.encodeNative(tag.text()),tag.text()==null?null:tag.text().getString(),tag.loggedName());
    }
    public static net.minecraft.text.Style hoverStyle(NativeMessageMeta.Tag tag){
        var text=tooltip(tag);
        return text==null?net.minecraft.text.Style.EMPTY:net.minecraft.text.Style.EMPTY.withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT,text));
    }
    public static RichText deletedMarker(){return RichText.of(Text.translatable("chat.deleted_marker").formatted(net.minecraft.util.Formatting.GRAY,net.minecraft.util.Formatting.ITALIC));}
    public static Text tooltip(NativeMessageMeta.Tag tag){
        if(tag==null)return null;
        var text=com.atom.chat.history.ChatHistory.decodeNative(tag.tooltipJson());
        return text!=null?text:tag.tooltipText()==null?null:Text.literal(tag.tooltipText());
    }
    public static MessageIndicator.Icon icon(NativeMessageMeta.Icon descriptor){
        if(descriptor==null)return null;
        for(var icon:MessageIndicator.Icon.values())if(icon.name().equals(descriptor.name()))return icon;
        return null;
    }
}
