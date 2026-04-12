package net.aqualoco.sec.network;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.server.level.ServerPlayer;

// Centralizes the authoritative managed-bed look sync path.
public final class BedLookNetworking {

    private BedLookNetworking() {
    }

    public static void handleServer(ServerPlayer player, BedLookSyncPayload payload) {
        if (!BedRestingHelper.isManagedBedStateServer(player) || player.getBedOrientation() == null) {
            return;
        }

        BedRestingHelper.setAuthoritativeBedLook(player, payload.yaw(), payload.pitch());
    }
}
