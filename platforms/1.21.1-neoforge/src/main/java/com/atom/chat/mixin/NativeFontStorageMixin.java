package com.atom.chat.mixin;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.render.NativeFontRenderer;
import com.mojang.serialization.JsonOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.gui.font.FontSet.class)
public abstract class NativeFontStorageMixin {
    @Inject(method="reload(Ljava/util/Set;)V",at=@At("RETURN"))
    private void atomchat$resolvedProviders(java.util.Set<net.minecraft.client.gui.font.FontOption> filters,CallbackInfo info){
        var access=(NativeFontStorageAccess)(Object)this;
        var active=new java.util.HashMap<String,Boolean>();for(var option:filters)active.put(option.getSerializedName(),true);
        NativeFontProvenance.fontStorage(this,access.atomchat$fontId().toString(),access.atomchat$allProviders().stream().filter(entry->entry.filter().apply(filters)).map(entry->entry.provider()).toList(),active,
                id->{var resource=net.minecraft.client.Minecraft.getInstance().getVanillaPackResources().asProvider().getResource(net.minecraft.resources.ResourceLocation.parse(id));return resource.isEmpty()?null:resource.get().open();},
                json->{var definition=net.minecraft.client.gui.font.providers.GlyphProviderDefinition.Conditional.CODEC.parse(JsonOps.INSTANCE,json).result().orElseThrow();
                    return net.minecraft.client.gui.font.providers.GlyphProviderDefinition.Conditional.CODEC.encodeStart(JsonOps.INSTANCE,definition).result().orElseThrow().getAsJsonObject();});
        NativeFontRenderer.invalidate();
    }
}
