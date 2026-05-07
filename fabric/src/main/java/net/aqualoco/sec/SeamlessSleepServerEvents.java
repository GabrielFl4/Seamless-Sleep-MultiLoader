package net.aqualoco.sec;

import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSync;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// Fabric-side server event hooks used to sync config to players when they join.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerConfigSync.sendToPlayer(handler.getPlayer(), SeamlessSleepServerConfigManager.get());
            ServerConfigMutationService.sendAccessToPlayer(handler.getPlayer());
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(handler.getPlayer());
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> SleepAnimationNetworking.sendActiveSnapshotToPlayer(player)
        );

        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> SleepAnimationNetworking.sendActiveSnapshotToPlayer(newPlayer)
        );
    }
}
