package net.aqualoco.sec;

import net.aqualoco.sec.registry.ModBlocks;
import net.aqualoco.sec.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SeamlessSleep implements ModInitializer {
    
    @Override
    public void onInitialize() {
        SeamlessSleepCommon.init();
        SeamlessSleepServerEvents.register();
        SeamlessSleepCommandRegistration.register();

        bind(BuiltInRegistries.BLOCK, ModBlocks::register);
        bind(BuiltInRegistries.ITEM, ModItems::register);
    }

    /** Adapted from <a href="https://github.com/VazkiiMods/Botania">Botania</a>*/
    private static <T> void bind(
            Registry<T> registry, Consumer<BiConsumer<T, ResourceLocation>> source) {
        source.accept((t, rl) -> Registry.register(registry, rl, t));
    }

}
