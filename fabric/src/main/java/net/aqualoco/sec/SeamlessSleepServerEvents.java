package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
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
                            cfg.worldSleepAcceleration.mode,
                            cfg.worldSleepAcceleration.preset,
                            cfg.worldSleepAcceleration.randomTickAccelerationEnabled,
                            cfg.worldSleepAcceleration.processAccelerationEnabled,
                            cfg.worldSleepAcceleration.governorAggressiveness,
                            cfg.worldSleepAcceleration.natureFilterProfile,
                            cfg.worldSleepAcceleration.nature.baseRadiusChunks,
                            cfg.worldSleepAcceleration.nature.autoMinRadiusChunks,
                            cfg.worldSleepAcceleration.nature.baseRateFraction,
                            cfg.worldSleepAcceleration.nature.autoMinRateFraction,
                            cfg.worldSleepAcceleration.process.baseRadiusChunks,
                            cfg.worldSleepAcceleration.process.autoMinRadiusChunks,
                            cfg.worldSleepAcceleration.process.baseRateFraction,
                            cfg.worldSleepAcceleration.process.autoMinRateFraction
                    ));
        });
    }
}
