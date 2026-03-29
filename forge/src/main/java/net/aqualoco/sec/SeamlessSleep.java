package net.aqualoco.sec;

import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.registry.ModBlocks;
import net.aqualoco.sec.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static BusGroup modBusGroup;

    public SeamlessSleep(FMLJavaModLoadingContext context) {
        modBusGroup = context.getModBusGroup();
        SeamlessSleepCommon.init();

        bind(Registries.BLOCK, ModBlocks::register);
        bind(Registries.ITEM, ModItems::register);

        RegisterCommandsEvent.BUS.addListener(SeamlessSleepCommandRegistration::register);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(context);
        }
    }

    private static <T> void bind(ResourceKey<Registry<T>> registry, Consumer<BiConsumer<T, Identifier>> source) {
        RegisterEvent.getBus(modBusGroup).addListener((RegisterEvent event) -> {
            if (registry.equals(event.getRegistryKey())) {
                source.accept((entry, id) -> event.register(registry, id, () -> entry));
            }
        });
    }

    private static final class ClientInit {
        private ClientInit() {
        }

        private static void init(FMLJavaModLoadingContext context) {
            SleepAnimationNetworking.initClient();
            net.aqualoco.sec.client.ForgeConfigScreens.register(context.getContainer());
        }
    }
}
