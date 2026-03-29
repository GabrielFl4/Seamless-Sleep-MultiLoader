package net.aqualoco.sec;

import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

// Fabric-side bootstrap for client networking. Rendering/ticking is handled from common 26.1 mixins.
@Environment(EnvType.CLIENT)
public class SeamlessSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SleepAnimationNetworking.initClient();
    }
}
