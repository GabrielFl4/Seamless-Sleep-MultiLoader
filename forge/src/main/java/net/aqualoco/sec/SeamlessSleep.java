package net.aqualoco.sec;

import net.aqualoco.sec.client.ForgeClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge bootstrap that initializes shared logic and wires Forge events.
@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public SeamlessSleep(FMLJavaModLoadingContext context) {

        SeamlessSleepCommon.init();

        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepCommandRegistration::register);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SeamlessSleepServerEvents::onServerStopping);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientBootstrap.init(context);
        }
    }
}
