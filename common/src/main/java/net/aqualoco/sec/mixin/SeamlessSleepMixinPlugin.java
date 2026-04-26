package net.aqualoco.sec.mixin;

import net.aqualoco.sec.platform.Services;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// Gates optional compat mixins so the base config stays loadable without external mods on the classpath.
public final class SeamlessSleepMixinPlugin implements IMixinConfigPlugin {

    private static final String BETTER_CLOUDS_MOD_ID = "betterclouds";
    private static final String BETTER_CLOUDS_MIXIN = "net.aqualoco.sec.mixin.client.render.compat.BetterCloudsRendererSleepAccelerationMixin";
    private static final String BETTER_CLOUDS_RENDERER_RESOURCE = "com/qendolin/betterclouds/clouds/Renderer.class";
    private static final String ABSTRACT_FURNACE_ACCELERATION_MIXIN = "net.aqualoco.sec.mixin.sleep.AbstractFurnaceBlockEntityAccelerationMixin";
    private static final String FORGE_LOADER_RESOURCE = "net/minecraftforge/fml/loading/FMLLoader.class";

    private boolean betterCloudsAvailable;

    @Override
    public void onLoad(String mixinPackage) {
        betterCloudsAvailable = isBetterCloudsPresent();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (BETTER_CLOUDS_MIXIN.equals(mixinClassName)) {
            return betterCloudsAvailable && !isNeoForgePlatform();
        }
        if (ABSTRACT_FURNACE_ACCELERATION_MIXIN.equals(mixinClassName)) {
            return !isForgePlatform();
        }
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

    private boolean isBetterCloudsPresent() {
        try {
            if (Services.PLATFORM.isModLoaded(BETTER_CLOUDS_MOD_ID)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return hasClassResource(BETTER_CLOUDS_RENDERER_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(BETTER_CLOUDS_RENDERER_RESOURCE, SeamlessSleepMixinPlugin.class.getClassLoader());
    }

    private boolean isNeoForgePlatform() {
        try {
            return "NeoForge".equalsIgnoreCase(Services.PLATFORM.getPlatformName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isForgePlatform() {
        try {
            return "Forge".equalsIgnoreCase(Services.PLATFORM.getPlatformName());
        } catch (Throwable ignored) {
        }

        return hasClassResource(FORGE_LOADER_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(FORGE_LOADER_RESOURCE, SeamlessSleepMixinPlugin.class.getClassLoader());
    }

    private static boolean hasClassResource(String classResourcePath, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return classLoader.getResource(classResourcePath) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
