package com.atemukesu.extendednoteblock.bridgeclient.litematica;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public final class LitematicaMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) { }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("extendednoteblock_bridge_client")
                && loader.getModContainer("litematica").map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("0.28.8")).orElse(false)
                && loader.getModContainer("malilib").map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("0.29.6")).orElse(false);
    }
    public void acceptTargets(Set<String> mine, Set<String> others) { }
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
