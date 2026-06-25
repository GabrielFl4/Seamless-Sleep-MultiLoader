package net.aqualoco.sec;


import net.aqualoco.sec.client.NeoForgeConfigScreens;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// NeoForge bootstrap that initializes shared logic and wires NeoForge events.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static IEventBus eventBus;

    public SeamlessSleep(IEventBus eventBus, ModContainer modContainer) {

        SeamlessSleep.eventBus = eventBus;
        SeamlessSleepCommon.init();

        NeoForge.EVENT_BUS.addListener(SeamlessSleepCommandRegistration::register);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onServerStopping);

        if (FMLLoader.getCurrent().getDist().isClient()) {
            VivecraftClientCompat.registerClientIntegrations();
            SleepAnimationNetworking.initClient();
            NeoForgeConfigScreens.register(modContainer);
        }
    }
}
