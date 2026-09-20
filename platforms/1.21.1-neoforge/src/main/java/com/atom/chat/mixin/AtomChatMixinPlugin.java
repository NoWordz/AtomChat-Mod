package com.atom.chat.mixin;

import com.atom.chat.util.AwtDisplay;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Claims the process-wide AWT toolkit as early as a mod can: Mixin instantiates
 * config plugins before Minecraft's {@code Main} runs its static initialiser.
 * That head start is the whole point - by mod-construction time the cached
 * headless answer may already be locked (issue #19), and no later hook is early
 * enough. The rest of the plugin is inert.
 *
 * <p>Client-only by construction: on a dedicated server the dist check skips the
 * claim, so the server never touches AWT. This class runs before any mod class
 * is constructed, so it may only use the JDK and the loader API.
 */
public class AtomChatMixinPlugin implements IMixinConfigPlugin {
    static {
        if (FMLEnvironment.dist.isClient()) {
            AwtDisplay.claim();
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
