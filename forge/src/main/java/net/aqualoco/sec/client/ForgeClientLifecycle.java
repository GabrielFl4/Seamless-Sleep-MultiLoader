package net.aqualoco.sec.client;

import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

public final class ForgeClientLifecycle {

    private static boolean disconnectResetRegistered;

    private ForgeClientLifecycle() {
    }

    public static void registerDisconnectReset() {
        if (disconnectResetRegistered) {
            return;
        }
        disconnectResetRegistered = true;
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(ForgeClientLifecycle::onLoggingOut);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SeamlessSleepClientState.SLEEP_ANIMATION.reset();
        SeamlessSleepServerConfigSnapshot.reset();
    }
}
