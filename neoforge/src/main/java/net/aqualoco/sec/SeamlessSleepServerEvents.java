package net.aqualoco.sec;

import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// NeoForge-side server event hooks used to sync config when players log in.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getServer() == null) {
            return;
        }

        ServerSeamlessClientPresenceManager.beginHandshake(player);
    }

    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerSeamlessClientPresenceManager.handleDisconnect(player);
        }
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ServerSeamlessClientPresenceManager.isConfirmed(player)) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }

    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ServerSeamlessClientPresenceManager.isConfirmed(player)) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        ServerSeamlessClientPresenceManager.tick(event.getServer());
    }

    static void onServerStopping(ServerStoppingEvent event) {
        ServerSeamlessClientPresenceManager.reset();
    }
}
