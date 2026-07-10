package net.aqualoco.sec.client;

import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.neoforged.fml.ModContainer;

// Keeps NeoForge's common mod entrypoint free of direct client-only class initialization.
public final class NeoForgeClientBootstrap {

    private NeoForgeClientBootstrap() {
    }

    public static void init(ModContainer modContainer) {
        VivecraftClientCompat.registerClientIntegrations();
        SleepAnimationNetworking.initClient();
        NeoForgeConfigScreens.register(modContainer);
    }
}
