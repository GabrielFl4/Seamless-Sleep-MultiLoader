package net.aqualoco.sec;

import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// Fabric-side server event hooks used to sync config to players when they join.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerSeamlessClientPresenceManager.beginHandshake(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ServerSeamlessClientPresenceManager.handleDisconnect(handler.getPlayer()));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> {
                    if (ServerSeamlessClientPresenceManager.isConfirmed(player)) {
                        SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
                    }
                }
        );

        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> {
                    if (ServerSeamlessClientPresenceManager.isConfirmed(newPlayer)) {
                        SleepAnimationNetworking.sendActiveSnapshotToPlayer(newPlayer);
                    }
                }
        );

        ServerTickEvents.END_SERVER_TICK.register(ServerSeamlessClientPresenceManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerSeamlessClientPresenceManager.reset());
    }
}
