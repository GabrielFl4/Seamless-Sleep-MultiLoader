package net.aqualoco.sec;


import net.aqualoco.sec.client.NeoForgeConfigScreens;
import net.aqualoco.sec.registry.ModBlocks;
import net.aqualoco.sec.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static IEventBus eventBus;

    public SeamlessSleep(IEventBus eventBus, ModContainer modContainer) {

        SeamlessSleep.eventBus = eventBus;
        SeamlessSleepCommon.init();

        bind(Registries.BLOCK, ModBlocks::register);
        bind(Registries.ITEM, ModItems::register);

        if (seamlesssleep$isClient()) {
            NeoForgeConfigScreens.register(modContainer);
        }
    }

    /** Adapted from <a href="https://github.com/VazkiiMods/Botania">Botania</a>*/
    private static <T> void bind(ResourceKey<Registry<T>> registry, Consumer<BiConsumer<T, ResourceLocation>> source) {
        eventBus.addListener((RegisterEvent event) -> {
            if (registry.equals(event.getRegistryKey())) {
                source.accept((t, rl) -> event.register(registry, rl, () -> t));
            }
        });
    }

    private static boolean seamlesssleep$isClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft", false, SeamlessSleep.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
