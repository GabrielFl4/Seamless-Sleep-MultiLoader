package net.aqualoco.sec.network;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.minecraft.server.level.ServerPlayer;

// Centralizes the authoritative managed-bed look sync path.
public final class BedLookNetworking {

    private BedLookNetworking() {
    }

    public static void handleServer(ServerPlayer player, BedLookSyncPayload payload) {
        if (!ServerSeamlessClientPresenceManager.requireConfirmed(player, "bed_look_sync")) {
            return;
        }
        if (!BedRestingHelper.isManagedBedStateServer(player)
                || VivecraftCompat.isServerVrActive(player)
                || player.getBedOrientation() == null) {
            return;
        }

        BedRestingHelper.setAuthoritativeBedLook(player, payload.yaw(), payload.pitch());
    }
}
