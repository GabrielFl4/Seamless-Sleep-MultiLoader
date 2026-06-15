package net.aqualoco.sec.mixin;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.compat.BetterDaysCompat;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.platform.Services;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// Gates optional compat mixins so the base config stays loadable without external mods on the classpath.
public final class SeamlessSleepMixinPlugin implements IMixinConfigPlugin {

    private static final String BETTER_CLOUDS_MOD_ID = "betterclouds";
    private static final String REACTIVE_MUSIC_MOD_ID = "reactive_music";
    private static final String BETTER_CLOUDS_MIXIN = "net.aqualoco.sec.mixin.client.render.compat.BetterCloudsRendererSleepAccelerationMixin";
    private static final String BETTER_CLOUDS_RENDERER_RESOURCE = "com/qendolin/betterclouds/clouds/Renderer.class";
    private static final String BETTER_DAYS_MIXIN = "net.aqualoco.sec.mixin.compat.BetterDaysSleepFeatureMixin";
    private static final String BETTER_DAYS_SLEEP_STATUS_MIXIN = "net.aqualoco.sec.mixin.compat.BetterDaysSleepStatusMixin";
    private static final String REACTIVE_MUSIC_PLAYER_THREAD_DUCK_MIXIN = "net.aqualoco.sec.mixin.compat.reactivemusic.ReactiveMusicPlayerThreadDuckMixin";
    private static final String REACTIVE_MUSIC_PLAYER_THREAD_RESOURCE = "circuitlord/reactivemusic/PlayerThread.class";
    private static final String VIVECRAFT_POST_PROCESS_UBO_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftPostProcessUboMixin";
    private static final String VIVECRAFT_LOCAL_PLAYER_ROOM_Y_OFFSET_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftLocalPlayerRoomYOffsetMixin";
    private static final String VIVECRAFT_INTERACT_TRACKER_SLEEP_GATE_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftInteractTrackerSleepGateMixin";
    private static final String VIVECRAFT_VR_PLAYER_MENU_HAND_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftVRPlayerMenuHandMixin";
    private static final String VIVECRAFT_VR_PLAYER_MODEL_SLEEPING_OFFSET_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftVRPlayerModelSleepingOffsetMixin";
    private static final String VIVECRAFT_MODEL_UTILS_SLEEPING_OFFSET_MIXIN = "net.aqualoco.sec.mixin.compat.vivecraft.VivecraftModelUtilsSleepingOffsetMixin";
    private static final String ABSTRACT_FURNACE_ACCELERATION_MIXIN = "net.aqualoco.sec.mixin.sleep.AbstractFurnaceBlockEntityAccelerationMixin";
    private static final String FORGE_LOADER_RESOURCE = "net/minecraftforge/fml/loading/FMLLoader.class";

    private boolean betterCloudsAvailable;
    private boolean betterDaysAvailable;
    private boolean betterDaysSleepStatusAvailable;
    private boolean reactiveMusicPlayerThreadAvailable;
    private boolean vivecraftPostProcessUboAvailable;
    private boolean vivecraftPlayerExtensionAvailable;
    private boolean vivecraftInteractTrackerAvailable;
    private boolean vivecraftVrPlayerAvailable;
    private boolean vivecraftVrPlayerSleepingOffsetTargetsAvailable;

    @Override
    public void onLoad(String mixinPackage) {
        betterCloudsAvailable = isBetterCloudsPresent();
        betterDaysAvailable = isBetterDaysPresent();
        betterDaysSleepStatusAvailable = isBetterDaysSleepStatusPresent();
        reactiveMusicPlayerThreadAvailable = isReactiveMusicPlayerThreadPresent();
        vivecraftPostProcessUboAvailable = isVivecraftTargetPresent(VivecraftCompat.POST_PROCESS_UBO_RESOURCE);
        vivecraftPlayerExtensionAvailable = isVivecraftTargetPresent(VivecraftCompat.PLAYER_EXTENSION_RESOURCE);
        vivecraftInteractTrackerAvailable = isVivecraftTargetPresent(VivecraftCompat.INTERACT_TRACKER_RESOURCE);
        vivecraftVrPlayerAvailable = isVivecraftTargetPresent(VivecraftCompat.VR_PLAYER_RESOURCE);
        vivecraftVrPlayerSleepingOffsetTargetsAvailable =
                isVivecraftTargetPresent(VivecraftCompat.VR_PLAYER_MODEL_RESOURCE)
                        && isVivecraftTargetPresent(VivecraftCompat.MODEL_UTILS_RESOURCE);
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
        if (BETTER_DAYS_MIXIN.equals(mixinClassName)) {
            return betterDaysAvailable;
        }
        if (BETTER_DAYS_SLEEP_STATUS_MIXIN.equals(mixinClassName)) {
            return betterDaysSleepStatusAvailable;
        }
        if (REACTIVE_MUSIC_PLAYER_THREAD_DUCK_MIXIN.equals(mixinClassName)) {
            return reactiveMusicPlayerThreadAvailable;
        }
        if (VIVECRAFT_POST_PROCESS_UBO_MIXIN.equals(mixinClassName)) {
            return vivecraftPostProcessUboAvailable;
        }
        if (VIVECRAFT_LOCAL_PLAYER_ROOM_Y_OFFSET_MIXIN.equals(mixinClassName)) {
            return vivecraftPlayerExtensionAvailable;
        }
        if (VIVECRAFT_INTERACT_TRACKER_SLEEP_GATE_MIXIN.equals(mixinClassName)) {
            return vivecraftInteractTrackerAvailable;
        }
        if (VIVECRAFT_VR_PLAYER_MENU_HAND_MIXIN.equals(mixinClassName)) {
            return vivecraftVrPlayerAvailable;
        }
        if (VIVECRAFT_VR_PLAYER_MODEL_SLEEPING_OFFSET_MIXIN.equals(mixinClassName)
                || VIVECRAFT_MODEL_UTILS_SLEEPING_OFFSET_MIXIN.equals(mixinClassName)) {
            return vivecraftVrPlayerSleepingOffsetTargetsAvailable;
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

    private boolean isBetterDaysPresent() {
        boolean modLoaded = isModLoaded(BetterDaysCompat.MOD_ID);
        boolean targetPresent = hasClassResource(BetterDaysCompat.TARGET_CLASS_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(BetterDaysCompat.TARGET_CLASS_RESOURCE, SeamlessSleepMixinPlugin.class.getClassLoader());

        if (modLoaded && !targetPresent) {
            Constants.warn("Better Days detected, but target betterdays.config.ConfigHandler$Common.enableSleepFeature() was not found; Better Days sleep compatibility could not be applied.");
        }
        return modLoaded && targetPresent;
    }

    private boolean isBetterDaysSleepStatusPresent() {
        boolean modLoaded = isModLoaded(BetterDaysCompat.MOD_ID);
        boolean targetPresent = hasClassResource(BetterDaysCompat.SLEEP_STATUS_CLASS_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(BetterDaysCompat.SLEEP_STATUS_CLASS_RESOURCE, SeamlessSleepMixinPlugin.class.getClassLoader());

        if (modLoaded && !targetPresent) {
            Constants.warn("Better Days detected, but target betterdays.time.SleepStatus was not found; Better Days sleep status compatibility could not be applied.");
        }
        return modLoaded && targetPresent;
    }

    private boolean isReactiveMusicPlayerThreadPresent() {
        boolean modLoaded = isModLoaded(REACTIVE_MUSIC_MOD_ID);
        boolean targetPresent = hasClassResource(REACTIVE_MUSIC_PLAYER_THREAD_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(REACTIVE_MUSIC_PLAYER_THREAD_RESOURCE, SeamlessSleepMixinPlugin.class.getClassLoader());

        if (modLoaded && !targetPresent) {
            Constants.warn("Reactive Music detected, but target {} was not found; Made in Heaven music ducking will not affect Reactive Music.", REACTIVE_MUSIC_PLAYER_THREAD_RESOURCE);
        }
        return targetPresent;
    }

    private boolean isVivecraftTargetPresent(String classResourcePath) {
        boolean modLoaded = isModLoaded(VivecraftCompat.MOD_ID);
        boolean targetPresent = hasClassResource(classResourcePath, Thread.currentThread().getContextClassLoader())
                || hasClassResource(classResourcePath, SeamlessSleepMixinPlugin.class.getClassLoader());

        if (modLoaded && !targetPresent) {
            Constants.warn("Vivecraft detected, but target {} was not found; matching Vivecraft compatibility hook will not be applied.", classResourcePath);
        }
        return modLoaded && targetPresent;
    }

    private boolean isModLoaded(String modId) {
        try {
            return Services.PLATFORM.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
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
