package net.aqualoco.sec;


import net.aqualoco.sec.client.NeoForgeConfigScreens;
import net.aqualoco.sec.registry.ModBlocks;
import net.aqualoco.sec.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// NeoForge 1.20.1 still exposes Forge package names, so bootstrap stays close to Forge.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static IEventBus eventBus;

    public SeamlessSleep() {

        eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SeamlessSleepCommon.init();

        bind(Registries.BLOCK, ModBlocks::register);
        bind(Registries.ITEM, ModItems::register);

        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepCommandRegistration::register);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeConfigScreens.register(ModLoadingContext.get());
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
