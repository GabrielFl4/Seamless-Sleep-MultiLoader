package net.aqualoco.sec.client;

import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Keeps Forge's common mod entrypoint free of direct client-only class initialization.
public final class ForgeClientBootstrap {

    private ForgeClientBootstrap() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        VivecraftClientCompat.registerClientIntegrations();
        SleepAnimationNetworking.initClient();
        ForgeConfigScreens.register(context);
    }
}
