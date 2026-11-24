package com.atom.chat.mixin;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.render.NativeFontRenderer;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.font.FontLoader;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.util.concurrent.*;

@Mixin(net.minecraft.client.font.FontManager.class)
public abstract class NativeFontManagerMixin {
    @Inject(method="loadFontProviders", at=@At("RETURN"))
    private static void atomchat$definitions(List<Resource> resources, Identifier font,
            CallbackInfoReturnable<List<Pair<?, ?>>> result) {
        for (Pair<?, ?> pair : result.getReturnValue()) {
            var definition = (FontLoader.Provider) pair.getSecond();
            JsonObject json = FontLoader.Provider.CODEC.encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow().getAsJsonObject();
            NativeFontProvenance.definition(pair.getFirst(), font.toString(), json);
        }
    }
    @Inject(method="load", at=@At("RETURN"), cancellable=true)
    private void atomchat$loaded(@Coerce Object key, FontLoader.Loadable loader,
            ResourceManager resources, Executor executor,
            CallbackInfoReturnable<CompletableFuture<Optional<net.minecraft.client.font.Font>>> result) {
        var definitionToken = NativeFontProvenance.take(key);
        var builtin = net.minecraft.client.MinecraftClient.getInstance().getDefaultResourcePack().getFactory();
        result.setReturnValue(result.getReturnValue().thenApply(optional -> {
            optional.ifPresent(provider -> NativeFontProvenance.loaded(definitionToken, provider, resources,
                    id -> resources.getResource(Identifier.of(id)).orElseThrow().getInputStream(),
                    id -> builtin.getResource(Identifier.of(id)).map(resource -> {
                        try { return resource.getInputStream(); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }).orElse(null),
                    json -> {
                        var definition = FontLoader.Provider.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        return FontLoader.Provider.CODEC.encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow().getAsJsonObject();
                    }));
            return optional;
        }));
    }
    @Inject(method="loadIndex",at=@At("HEAD"))
    private void atomchat$beginReload(CallbackInfoReturnable<?> info){NativeFontProvenance.beginReload();}
    @Inject(method="reload(Lnet/minecraft/client/font/FontManager$ProviderIndex;Lnet/minecraft/util/profiler/Profiler;)V",at=@At("HEAD"))
    private void atomchat$publish(@Coerce Object preparation, net.minecraft.util.profiler.Profiler profiler, CallbackInfo info) {
        NativeFontProvenance.publish(((NativeFontPreparationAccess)preparation).atomchat$providers());
    }
    @Inject(method={"reload(Lnet/minecraft/client/font/FontManager$ProviderIndex;Lnet/minecraft/util/profiler/Profiler;)V", "setActiveFilters"}, at=@At("HEAD"))
    private void atomchat$invalidate(CallbackInfo info) { NativeFontRenderer.invalidate(); }
}
