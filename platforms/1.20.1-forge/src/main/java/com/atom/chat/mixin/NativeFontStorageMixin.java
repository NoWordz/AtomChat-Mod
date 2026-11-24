package com.atom.chat.mixin;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.render.NativeFontRenderer;
import com.mojang.serialization.JsonOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.gui.font.FontSet.class)
public abstract class NativeFontStorageMixin {
    @Inject(method="reload(Ljava/util/List;)V",at=@At("RETURN"))
    private void atomchat$resolvedProviders(java.util.List<com.mojang.blaze3d.font.GlyphProvider> providers,CallbackInfo info){
        var access=(NativeFontStorageAccess)(Object)this;
        var active=new java.util.HashMap<String,Boolean>();
        NativeFontProvenance.fontStorage(this,access.atomchat$fontId().toString(),providers,active,
                id->{var resource=net.minecraft.client.Minecraft.getInstance().getVanillaPackResources().asProvider().getResource(new net.minecraft.resources.ResourceLocation(id));return resource.isEmpty()?null:resource.get().open();},
                json->{var definition=net.minecraft.client.gui.font.providers.GlyphProviderDefinition.CODEC.parse(JsonOps.INSTANCE,json).result().orElseThrow();
                    return net.minecraft.client.gui.font.providers.GlyphProviderDefinition.CODEC.encodeStart(JsonOps.INSTANCE,definition).result().orElseThrow().getAsJsonObject();});
        NativeFontRenderer.invalidate();
    }
}
