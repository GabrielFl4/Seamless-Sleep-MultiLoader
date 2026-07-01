package net.aqualoco.sec;

import net.aqualoco.sec.client.ForgeConfigScreens;
import net.aqualoco.sec.client.ForgeHudOverlayLayers;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge bootstrap that initializes shared logic and wires Forge events.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public static BusGroup modBusGroup;

    public SeamlessSleep(FMLJavaModLoadingContext context) {

        modBusGroup = context.getModBusGroup();
        SeamlessSleepCommon.init();

        RegisterCommandsEvent.BUS.addListener(SeamlessSleepCommandRegistration::register);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedOut);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(SeamlessSleepServerEvents::onPlayerChangedDimension);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(SeamlessSleepServerEvents::onPlayerRespawn);
        TickEvent.ServerTickEvent.Post.BUS.addListener(SeamlessSleepServerEvents::onServerTick);
        ServerStoppingEvent.BUS.addListener(SeamlessSleepServerEvents::onServerStopping);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            VivecraftClientCompat.registerClientIntegrations();
            SleepAnimationNetworking.initClient();
            ForgeConfigScreens.register(context);
            ForgeHudOverlayLayers.register(modBusGroup);
        }
    }
}
