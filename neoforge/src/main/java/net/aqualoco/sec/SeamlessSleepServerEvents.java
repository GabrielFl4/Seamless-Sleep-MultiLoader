package net.aqualoco.sec;

import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSync;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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

        ServerConfigSync.sendToPlayer(player, SeamlessSleepServerConfigManager.get());
        ServerConfigMutationService.sendAccessToPlayer(player);
        SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }

    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }
}
