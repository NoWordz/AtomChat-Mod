package com.atom.chat.mixin;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.render.NativeFontRenderer;
import com.mojang.serialization.JsonOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.font.FontStorage.class)
public abstract class NativeFontStorageMixin {
    @Inject(method="setActiveFilters(Ljava/util/Set;)V",at=@At("RETURN"))
    private void atomchat$resolvedProviders(java.util.Set<net.minecraft.client.font.FontFilterType> filters,CallbackInfo info){
        var access=(NativeFontStorageAccess)(Object)this;
        var active=new java.util.HashMap<String,Boolean>();for(var option:filters)active.put(option.asString(),true);
        NativeFontProvenance.fontStorage(this,access.atomchat$fontId().toString(),access.atomchat$allProviders().stream().filter(entry->entry.filter().isAllowed(filters)).map(entry->entry.provider()).toList(),active,
                id->{var resource=net.minecraft.client.MinecraftClient.getInstance().getDefaultResourcePack().getFactory().getResource(net.minecraft.util.Identifier.of(id));return resource.isEmpty()?null:resource.get().getInputStream();},
                json->{var definition=net.minecraft.client.font.FontLoader.Provider.CODEC.parse(JsonOps.INSTANCE,json).result().orElseThrow();
                    return net.minecraft.client.font.FontLoader.Provider.CODEC.encodeStart(JsonOps.INSTANCE,definition).result().orElseThrow().getAsJsonObject();});
        NativeFontRenderer.invalidate();
    }
}
