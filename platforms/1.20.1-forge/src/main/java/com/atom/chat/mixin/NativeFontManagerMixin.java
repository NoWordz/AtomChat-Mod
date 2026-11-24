package com.atom.chat.mixin;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.render.NativeFontRenderer;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.util.concurrent.*;

@Mixin(net.minecraft.client.gui.font.FontManager.class)
public abstract class NativeFontManagerMixin {
    @Inject(method="loadResourceStack", at=@At("RETURN"))
    private static void atomchat$definitions(List<Resource> resources, ResourceLocation font,
            CallbackInfoReturnable<List<Pair<?, ?>>> result) {
        for (Pair<?, ?> pair : result.getReturnValue()) {
            var definition = (GlyphProviderDefinition) pair.getSecond();
            JsonObject json = GlyphProviderDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow().getAsJsonObject();
            NativeFontProvenance.definition(pair.getFirst(), font.toString(), json);
        }
    }
    @Inject(method="safeLoad", at=@At("RETURN"), cancellable=true)
    private void atomchat$loaded(@Coerce Object key, GlyphProviderDefinition.Loader loader,
            ResourceManager resources, Executor executor,
            CallbackInfoReturnable<CompletableFuture<Optional<com.mojang.blaze3d.font.GlyphProvider>>> result) {
        var definitionToken = NativeFontProvenance.take(key);
        var builtin = net.minecraft.client.Minecraft.getInstance().getVanillaPackResources().asProvider();
        result.setReturnValue(result.getReturnValue().thenApply(optional -> {
            optional.ifPresent(provider -> NativeFontProvenance.loaded(definitionToken, provider, resources,
                    id -> resources.getResource(new ResourceLocation(id)).orElseThrow().open(),
                    id -> builtin.getResource(new ResourceLocation(id)).map(resource -> {
                        try { return resource.open(); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }).orElse(null),
                    json -> {
                        var definition = GlyphProviderDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        return GlyphProviderDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow().getAsJsonObject();
                    }));
            return optional;
        }));
    }
    @Inject(method="prepare",at=@At("HEAD"))
    private void atomchat$beginReload(CallbackInfoReturnable<?> info){NativeFontProvenance.beginReload();}
    @Inject(method="apply",at=@At("HEAD"))
    private void atomchat$publish(@Coerce Object preparation, net.minecraft.util.profiling.ProfilerFiller profiler, CallbackInfo info) {
        NativeFontProvenance.publish(((NativeFontPreparationAccess)preparation).atomchat$providers());
    }
    @Inject(method={"apply", "setRenames"}, at=@At("HEAD"))
    private void atomchat$invalidate(CallbackInfo info) { NativeFontRenderer.invalidate(); }
}
