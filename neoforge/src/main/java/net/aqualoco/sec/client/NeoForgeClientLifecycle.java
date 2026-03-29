package net.aqualoco.sec.client;

import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientLifecycle {

    private static boolean disconnectResetRegistered;

    private NeoForgeClientLifecycle() {
    }

    public static void registerDisconnectReset() {
        if (disconnectResetRegistered) {
            return;
        }
        disconnectResetRegistered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgeClientLifecycle::onLoggingOut);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SeamlessSleepClientState.SLEEP_ANIMATION.reset();
        SeamlessSleepServerConfigSnapshot.reset();
    }
}
