package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

// Fabric-side server event hooks used to sync config to players when they join.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
            ServerPlayNetworking.send(handler.getPlayer(),
                    new ServerConfigSyncPayload(
                            cfg.sleepWeatherClearChancePercent,
                            cfg.sleepAnimationDurationMultiplier,
                            Math.max(1, server.getPlayerList().getSimulationDistance()),
                            cfg.worldSleepAcceleration.mode,
                            cfg.worldSleepAcceleration.automaticMode,
                            cfg.worldSleepAcceleration.playersAffected,
                            cfg.worldSleepAcceleration.manualAccelerationRadiusChunks,
                            cfg.worldSleepAcceleration.manualAccelerationSpeedPercent,
                            cfg.worldSleepAcceleration.grassAndFoliageAccelerationEnabled,
                            cfg.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled,
                            cfg.worldSleepAcceleration.kelpAccelerationEnabled,
                            cfg.worldSleepAcceleration.vanillaOnlyAcceleration,
                            cfg.worldSleepAcceleration.processesAccelerationEnabled,
                            cfg.worldSleepAcceleration.processesSpeedPercent
                    ));
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
