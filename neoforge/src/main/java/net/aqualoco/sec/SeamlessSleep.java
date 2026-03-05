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
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// NeoForge bootstrap that initializes shared logic and wires NeoForge events/registries.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static IEventBus eventBus;

    public SeamlessSleep(IEventBus eventBus, ModContainer modContainer) {

        SeamlessSleep.eventBus = eventBus;
        SeamlessSleepCommon.init();

        bind(Registries.BLOCK, ModBlocks::register);
        bind(Registries.ITEM, ModItems::register);

        NeoForge.EVENT_BUS.addListener(SeamlessSleepCommandRegistration::register);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);

        if (FMLLoader.getDist().isClient()) {
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

}
