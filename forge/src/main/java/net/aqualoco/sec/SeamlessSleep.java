package net.aqualoco.sec;

import net.aqualoco.sec.client.ForgeConfigScreens;
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

// Forge bootstrap that initializes shared logic and wires Forge events/registries.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static IEventBus eventBus;

    public SeamlessSleep(FMLJavaModLoadingContext context) {

        eventBus = context.getModEventBus();
        SeamlessSleepCommon.init();

        bind(Registries.BLOCK, ModBlocks::register);
        bind(Registries.ITEM, ModItems::register);

        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepCommandRegistration::register);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeConfigScreens.register(context);
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
